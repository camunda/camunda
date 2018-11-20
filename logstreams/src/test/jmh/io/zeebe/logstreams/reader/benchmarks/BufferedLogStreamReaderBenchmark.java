/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.reader.benchmarks;

import static io.zeebe.logstreams.reader.benchmarks.Benchmarks.DATA_SET_SIZE;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;

@BenchmarkMode(Mode.Throughput)
public class BufferedLogStreamReaderBenchmark {
  @Benchmark
  @Threads(1)
  public long iterateWithNewReader(
      FilledLogStreamAndReaderSupplier filledLogStreamAndReaderSupplier) {
    final BufferedLogStreamReader reader = filledLogStreamAndReaderSupplier.reader;
    reader.seekToFirstEvent();

    long count = 0L;
    while (reader.hasNext()) {
      reader.next();
      count++;
    }
    if (count != DATA_SET_SIZE) {
      throw new IllegalStateException(
          "Iteration count " + count + " is not equal with data count " + DATA_SET_SIZE);
    }
    return count;
  }
}
