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
package io.zeebe.logstreams.processor;

import java.time.Duration;
import java.util.Objects;

import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.DisabledLogStreamWriter;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.snapshot.TimeBasedSnapshotPolicy;
import io.zeebe.logstreams.spi.SnapshotPolicy;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.sched.ZbActorScheduler;

public class StreamProcessorBuilder
{
    protected int id;
    protected String name;

    protected StreamProcessor streamProcessor;

    protected LogStream logStream;

    protected ZbActorScheduler actorScheduler;

    protected SnapshotPolicy snapshotPolicy;
    protected SnapshotStorage snapshotStorage;

    protected LogStreamReader logStreamReader;
    protected LogStreamWriter logStreamWriter;

    protected EventFilter eventFilter;
    protected EventFilter reprocessingEventFilter;

    protected DeferredCommandContext streamProcessorCmdQueue;

    protected boolean readOnly;

    public StreamProcessorBuilder(int id, String name, StreamProcessor streamProcessor)
    {
        this.id = id;
        this.name = name;
        this.streamProcessor = streamProcessor;
    }

    public StreamProcessorBuilder logStream(LogStream stream)
    {
        this.logStream = stream;
        return this;
    }

    public StreamProcessorBuilder actorScheduler(ZbActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
        return this;
    }

    public StreamProcessorBuilder snapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        this.snapshotPolicy = snapshotPolicy;
        return this;
    }

    public StreamProcessorBuilder snapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
        return this;
    }

    public StreamProcessorBuilder streamProcessorCmdQueue(DeferredCommandContext streamProcessorCmdQueue)
    {
        this.streamProcessorCmdQueue = streamProcessorCmdQueue;
        return this;
    }

    /**
     * @param eventFilter may be null to accept all events
     */
    public StreamProcessorBuilder eventFilter(EventFilter eventFilter)
    {
        this.eventFilter = eventFilter;
        return this;
    }

    public StreamProcessorBuilder readOnly(boolean readOnly)
    {
        this.readOnly = readOnly;
        return this;
    }

    /**
     * @param reprocessingEventFilter may be null to re-process all events
     */
    public StreamProcessorBuilder reprocessingEventFilter(EventFilter reprocessingEventFilter)
    {
        this.reprocessingEventFilter = reprocessingEventFilter;
        return this;
    }

    protected void initContext()
    {
        Objects.requireNonNull(streamProcessor, "No stream processor provided.");
        Objects.requireNonNull(logStream, "No log stream provided.");
        Objects.requireNonNull(actorScheduler, "No task scheduler provided.");
        Objects.requireNonNull(snapshotStorage, "No snapshot storage provided.");

        if (streamProcessorCmdQueue == null)
        {
            streamProcessorCmdQueue = new DeferredCommandContext(100);
        }

        if (snapshotPolicy == null)
        {
            snapshotPolicy = new TimeBasedSnapshotPolicy(Duration.ofMinutes(1));
        }

        logStreamReader = new BufferedLogStreamReader();

        if (readOnly)
        {
            logStreamWriter = new DisabledLogStreamWriter();
        }
        else
        {
            logStreamWriter = new LogStreamWriterImpl();
        }
    }

    public StreamProcessorController build()
    {
        initContext();

        final StreamProcessorContext ctx = new StreamProcessorContext();

        ctx.setId(id);
        ctx.setName(name);

        ctx.setStreamProcessor(streamProcessor);
        ctx.setStreamProcessorCmdQueue(streamProcessorCmdQueue);

        ctx.setLogStream(logStream);

        ctx.setActorScheduler(actorScheduler);

        ctx.setLogStreamReader(logStreamReader);
        ctx.setLogStreamWriter(logStreamWriter);

        ctx.setSnapshotPolicy(snapshotPolicy);
        ctx.setSnapshotStorage(snapshotStorage);

        ctx.setEventFilter(eventFilter);
        ctx.setReprocessingEventFilter(reprocessingEventFilter);
        ctx.setReadOnly(readOnly);

        return new StreamProcessorController(ctx);
    }
}
