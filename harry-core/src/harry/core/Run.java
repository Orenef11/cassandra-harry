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

package harry.core;

import harry.ddl.SchemaSpec;
import harry.model.OpSelectors;
import harry.model.sut.SystemUnderTest;
import harry.runner.DataTracker;
import harry.operations.QueryGenerator;

public class Run
{
    public final OpSelectors.Rng rng;
    public final OpSelectors.MonotonicClock clock;
    public final OpSelectors.PdSelector pdSelector;
    public final OpSelectors.DescriptorSelector descriptorSelector;
    public final QueryGenerator rangeSelector;

    public final SchemaSpec schemaSpec;
    public final DataTracker tracker;
    public final SystemUnderTest sut;

    public final MetricReporter metricReporter;


    public Run(OpSelectors.Rng rng,
               OpSelectors.MonotonicClock clock,
               OpSelectors.PdSelector pdSelector,
               OpSelectors.DescriptorSelector descriptorSelector,
               SchemaSpec schemaSpec,
               DataTracker tracker,
               SystemUnderTest sut,
               MetricReporter metricReporter)
    {
        this(rng, clock, pdSelector, descriptorSelector,
             new QueryGenerator(schemaSpec, pdSelector, descriptorSelector, rng),
             schemaSpec, tracker, sut, metricReporter);
    }

    private Run(OpSelectors.Rng rng,
                OpSelectors.MonotonicClock clock,
                OpSelectors.PdSelector pdSelector,
                OpSelectors.DescriptorSelector descriptorSelector,
                QueryGenerator rangeSelector,
                SchemaSpec schemaSpec,
                DataTracker tracker,
                SystemUnderTest sut,
                MetricReporter metricReporter)
    {

        this.rng = rng;
        this.clock = clock;
        this.pdSelector = pdSelector;
        this.descriptorSelector = descriptorSelector;
        this.rangeSelector = rangeSelector;
        this.schemaSpec = schemaSpec;
        this.tracker = tracker;
        this.sut = sut;
        this.metricReporter = metricReporter;
    }
}