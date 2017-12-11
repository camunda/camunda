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

import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.zeebe.logstreams.reader.benchmarks.Benchmarks.DATA_SET_SIZE;

/**
 *
 */
@State(Scope.Benchmark)
public class FilledLogStreamAndOldReaderSupplier
{

    private LogStream logStream;
    OldBufferedLogStreamReader reader;
    ActorScheduler actorScheduler;
    LogStreamWriterImpl writer;


    private long[] writeEvents(int count, DirectBuffer eventValue)
    {
        final long[] positions = new long[count];

        for (int i = 0; i < count; i++)
        {
            positions[i] = writeEvent(i, eventValue);
        }
        return positions;
    }
    private long writeEvent(long key, DirectBuffer eventValue)
    {
        long position = -1;
        while (position <= 0)
        {
            position = writer
                .key(key)
                .value(eventValue)
                .tryWrite();
        }

        return position;
    }

    @Setup(Level.Iteration)
    public void fillStream() throws IOException
    {
        reader = new OldBufferedLogStreamReader();

        final Path tempDirectory = Files.createTempDirectory("reader-benchmark");
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
        logStream = LogStreams.createFsLogStream(BufferUtil.wrapString("topic"), 0)
            .logDirectory(tempDirectory.toString())
            .actorScheduler(actorScheduler)
            .deleteOnClose(true)
            .build();

        logStream.open();
        logStream.setCommitPosition(Long.MAX_VALUE);

        writer = new LogStreamWriterImpl(logStream);
        final long[] positions = writeEvents(DATA_SET_SIZE, new UnsafeBuffer("test".getBytes()));

        final long lastPosition = positions[DATA_SET_SIZE - 1];
        while (logStream.getCurrentAppenderPosition() < lastPosition)
        {
            // spin
        }
        reader.wrap(logStream);
    }

    @TearDown(Level.Iteration)
    public void closeStream()
    {
        reader.close();
        logStream.close();
        actorScheduler.close();
    }
}
