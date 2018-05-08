/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.benchmarks.msgpack;

import java.util.concurrent.TimeUnit;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import io.zeebe.broker.job.data.JobEvent;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 20, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class POJOMappingBenchmark
{

    @Benchmark
    @Threads(1)
    public void performReadingOptimalOrder(POJOMappingContext ctx)
    {
        final JobEvent JobEvent = ctx.getJobEvent();
        final DirectBuffer encodedJobEvent = ctx.getOptimalOrderEncodedJobEvent();

        JobEvent.reset();
        JobEvent.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
    }

    @Benchmark
    @Threads(1)
    public void performReadingReverseOrder(POJOMappingContext ctx)
    {
        final JobEvent JobEvent = ctx.getJobEvent();
        final DirectBuffer encodedJobEvent = ctx.getReverseOrderEncodedJobEvent();

        JobEvent.reset();
        JobEvent.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
    }

    @Benchmark
    @Threads(1)
    public void performMappingCycleOptimalEncodedOrder(POJOMappingContext ctx) throws Exception
    {
        final JobEvent JobEvent = ctx.getJobEvent();
        final DirectBuffer encodedJobEvent = ctx.getOptimalOrderEncodedJobEvent();
        final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

        JobEvent.reset();
        JobEvent.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
        JobEvent.write(writeBuffer, 0);
    }

    @Benchmark
    @Threads(1)
    public void performMappingCycleReverseEncodedOrder(POJOMappingContext ctx) throws Exception
    {
        final JobEvent JobEvent = ctx.getJobEvent();
        final DirectBuffer encodedJobEvent = ctx.getReverseOrderEncodedJobEvent();
        final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

        JobEvent.reset();
        JobEvent.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
        JobEvent.write(writeBuffer, 0);
    }


}
