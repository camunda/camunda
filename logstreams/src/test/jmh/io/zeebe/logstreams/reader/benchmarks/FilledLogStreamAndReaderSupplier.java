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

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.sched.ActorScheduler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class FilledLogStreamAndReaderSupplier {
  LogStream logStream;
  ActorScheduler actorScheduler;
  LogStreamWriterImpl writer;
  ServiceContainerImpl serviceContainer;
  BufferedLogStreamReader reader = new BufferedLogStreamReader();

  private long[] writeEvents(final int count, final DirectBuffer eventValue) {
    final long[] positions = new long[count];

    for (int i = 0; i < count; i++) {
      positions[i] = writeEvent(i, eventValue);
    }
    return positions;
  }

  private long writeEvent(final long key, final DirectBuffer eventValue) {
    long position = -1;
    while (position <= 0) {
      position = writer.key(key).value(eventValue).tryWrite();
    }

    return position;
  }

  @Setup(Level.Iteration)
  public void fillStream() throws IOException {
    final Path tempDirectory = Files.createTempDirectory("reader-benchmark");
    actorScheduler = ActorScheduler.newDefaultActorScheduler();
    actorScheduler.start();

    serviceContainer = new ServiceContainerImpl(actorScheduler);
    serviceContainer.start();

    logStream =
        LogStreams.createFsLogStream(0)
            .logName("foo")
            .logDirectory(tempDirectory.toString())
            .serviceContainer(serviceContainer)
            .deleteOnClose(true)
            .build()
            .join();

    logStream.openAppender().join();

    logStream.setCommitPosition(Long.MAX_VALUE);

    writer = new LogStreamWriterImpl(logStream);
    final long[] positions = writeEvents(DATA_SET_SIZE, new UnsafeBuffer("test".getBytes()));

    final long lastPosition = positions[DATA_SET_SIZE - 1];
    final LogStorageAppender logStorageAppender = logStream.getLogStorageAppender();

    while (logStorageAppender.getCurrentAppenderPosition() < lastPosition) {
      // spin
    }

    reader.wrap(logStream);
  }

  @TearDown(Level.Iteration)
  public void closeStream() throws InterruptedException, ExecutionException, TimeoutException {
    reader.close();
    logStream.close();
    serviceContainer.close(10, TimeUnit.SECONDS);
    actorScheduler.stop();
  }
}
