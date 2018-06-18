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

import io.zeebe.broker.job.data.JobRecord;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class POJOMappingBenchmark {

  @Benchmark
  @Threads(1)
  public void performReadingOptimalOrder(final POJOMappingContext ctx) {
    final JobRecord jobRecord = ctx.getJobRecord();
    final DirectBuffer encodedJobEvent = ctx.getOptimalOrderEncodedJobEvent();

    jobRecord.reset();
    jobRecord.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
  }

  @Benchmark
  @Threads(1)
  public void performReadingReverseOrder(final POJOMappingContext ctx) {
    final JobRecord jobRecord = ctx.getJobRecord();
    final DirectBuffer encodedJobEvent = ctx.getReverseOrderEncodedJobEvent();

    jobRecord.reset();
    jobRecord.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
  }

  @Benchmark
  @Threads(1)
  public void performMappingCycleOptimalEncodedOrder(final POJOMappingContext ctx) {
    final JobRecord jobRecord = ctx.getJobRecord();
    final DirectBuffer encodedJobEvent = ctx.getOptimalOrderEncodedJobEvent();
    final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

    jobRecord.reset();
    jobRecord.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
    jobRecord.write(writeBuffer, 0);
  }

  @Benchmark
  @Threads(1)
  public void performMappingCycleReverseEncodedOrder(final POJOMappingContext ctx) {
    final JobRecord jobRecord = ctx.getJobRecord();
    final DirectBuffer encodedJobEvent = ctx.getReverseOrderEncodedJobEvent();
    final MutableDirectBuffer writeBuffer = ctx.getWriteBuffer();

    jobRecord.reset();
    jobRecord.wrap(encodedJobEvent, 0, encodedJobEvent.capacity());
    jobRecord.write(writeBuffer, 0);
  }
}
