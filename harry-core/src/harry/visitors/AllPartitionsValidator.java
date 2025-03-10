/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package harry.visitors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import harry.core.MetricReporter;
import harry.core.Run;
import harry.ddl.SchemaSpec;
import harry.model.Model;
import harry.model.OpSelectors;
import harry.model.sut.SystemUnderTest;
import harry.operations.Query;

// This might be something that potentially grows into the validator described in the design doc;
// right now it's just a helper/container class
public class AllPartitionsValidator implements Visitor
{
    private static final Logger logger = LoggerFactory.getLogger(AllPartitionsValidator.class);

    protected final Model model;
    protected final SchemaSpec schema;

    protected final OpSelectors.MonotonicClock clock;
    protected final OpSelectors.PdSelector pdSelector;
    protected final MetricReporter metricReporter;
    protected final ExecutorService executor;
    protected final SystemUnderTest sut;

    protected final AtomicBoolean condition;
    protected final AtomicLong maxPos;

    protected final int concurrency;
    protected final int triggerAfter;

    public AllPartitionsValidator(int concurrency,
                                  int triggerAfter,
                                  Run run,
                                  Model.ModelFactory modelFactory)
    {
        this.triggerAfter = triggerAfter;
        this.metricReporter = run.metricReporter;
        this.model = modelFactory.make(run);
        this.schema = run.schemaSpec;
        this.clock = run.clock;
        this.sut = run.sut;
        this.pdSelector = run.pdSelector;
        this.concurrency = concurrency;
        this.executor = Executors.newFixedThreadPool(concurrency);
        this.condition = new AtomicBoolean();
        this.maxPos = new AtomicLong(-1);
        run.tracker.onLtsStarted((lts) -> {
            maxPos.updateAndGet(current -> Math.max(pdSelector.positionFor(lts), current));
            if (triggerAfter == 0 || (triggerAfter > 0 && lts % triggerAfter == 0))
                condition.set(true);
        });
    }

    protected CompletableFuture<Void> validateAllPartitions(ExecutorService executor, int parallelism)
    {
        final long maxPos = this.maxPos.get();
        AtomicLong cnt = new AtomicLong();
        CompletableFuture<?>[] futures = new CompletableFuture[parallelism];
        AtomicBoolean isDone = new AtomicBoolean(false);

        for (int i = 0; i < parallelism; i++)
        {
            futures[i] = CompletableFuture.supplyAsync(() -> {
                long pos;
                while ((pos = cnt.getAndIncrement()) < maxPos && !executor.isShutdown() && !Thread.interrupted() && !isDone.get())
                {
                    if (pos > 0 && pos % 100 == 0)
                        logger.info(String.format("Validated %d out of %d partitions", pos, maxPos));
                    long visitLts = pdSelector.minLtsAt(pos);

                    metricReporter.validatePartition();

                    for (boolean reverse : new boolean[]{ true, false })
                    {
                        try
                        {
                            model.validate(Query.selectPartition(schema, pdSelector.pd(visitLts, schema), reverse));
                        }
                        catch (Throwable t)
                        {
                            isDone.set(true);
                            logger.error("Caught an error while validating all partitions.", t);
                            throw t;
                        }
                    }
                }
                return null;
            }, executor);
        }

        return CompletableFuture.allOf(futures);
    }

    public void visit()
    {
        // TODO: this is ok for now, but if/when we bring exhaustive checker back, we need to change this:
        // we'll probably want a low-concurrency validator somewhere in background all the time.
        if (condition.compareAndSet(true, false))
        {
            long lts = clock.peek();
            logger.info("Starting validation of all partitions as of lts {}...", lts);

            try
            {
                validateAllPartitions(executor, concurrency).get();
            }
            catch (Throwable e)
            {
                // TODO: Make a utility out of this.
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            logger.info("...finished validating all partitions as of lts {}.", lts);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void shutdown() throws InterruptedException
    {
        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);
    }
}