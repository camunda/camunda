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

import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.util.sched.ActorScheduler;

import java.time.Duration;
import java.util.Objects;

public class StreamProcessorBuilder
{
    protected int id;
    protected String name;

    protected StreamProcessor streamProcessor;

    protected LogStream logStream;

    protected ActorScheduler actorScheduler;

    protected Duration snapshotPeriod;
    protected SnapshotStorage snapshotStorage;

    protected LogStreamReader logStreamReader;
    protected LogStreamWriter logStreamWriter;

    protected EventFilter eventFilter;
    protected EventFilter reprocessingEventFilter;

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

    public StreamProcessorBuilder actorScheduler(ActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
        return this;
    }

    public StreamProcessorBuilder snapshotPeriod(Duration snapshotPeriod)
    {
        this.snapshotPeriod = snapshotPeriod;
        return this;
    }

    public StreamProcessorBuilder snapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
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

        if (snapshotPeriod == null)
        {
            snapshotPeriod = Duration.ofMinutes(1);
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

        ctx.setLogStream(logStream);

        ctx.setActorScheduler(actorScheduler);

        ctx.setLogStreamReader(logStreamReader);
        ctx.setLogStreamWriter(logStreamWriter);

        ctx.setSnapshotPeriod(snapshotPeriod);
        ctx.setSnapshotStorage(snapshotStorage);

        ctx.setEventFilter(eventFilter);
        ctx.setReprocessingEventFilter(reprocessingEventFilter);
        ctx.setReadOnly(readOnly);

        return new StreamProcessorController(ctx);
    }
}
