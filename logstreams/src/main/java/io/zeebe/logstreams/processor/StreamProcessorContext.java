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

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.spi.SnapshotPolicy;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.sched.ZbActorScheduler;

public class StreamProcessorContext
{
    protected int id;
    protected String name;

    protected StreamProcessor streamProcessor;
    protected boolean isReadOnlyProcessor;

    protected LogStream logStream;

    protected LogStreamReader logStreamReader;
    protected LogStreamWriter logStreamWriter;

    protected SnapshotPolicy snapshotPolicy;
    protected SnapshotStorage snapshotStorage;

    protected ZbActorScheduler actorScheduler;

    protected EventFilter eventFilter;
    protected EventFilter reprocessingEventFilter;

    protected DeferredCommandContext streamProcessorCmdContext;

    public LogStream getLogStream()
    {
        return logStream;
    }

    public void setLogStream(LogStream logstream)
    {
        this.logStream = logstream;
    }

    public StreamProcessor getStreamProcessor()
    {
        return streamProcessor;
    }

    public void setStreamProcessor(StreamProcessor streamProcessor)
    {
        this.streamProcessor = streamProcessor;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public ZbActorScheduler getActorScheduler()
    {
        return actorScheduler;
    }

    public void setActorScheduler(ZbActorScheduler actorScheduler)
    {
        this.actorScheduler = actorScheduler;
    }

    public void setLogStreamReader(LogStreamReader logStreamReader)
    {
        this.logStreamReader = logStreamReader;
    }

    public LogStreamReader getLogStreamReader()
    {
        return logStreamReader;
    }

    public LogStreamWriter getLogStreamWriter()
    {
        return logStreamWriter;
    }

    public void setLogStreamWriter(LogStreamWriter logStreamWriter)
    {
        this.logStreamWriter = logStreamWriter;
    }

    public SnapshotPolicy getSnapshotPolicy()
    {
        return snapshotPolicy;
    }

    public void setSnapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        this.snapshotPolicy = snapshotPolicy;
    }

    public SnapshotStorage getSnapshotStorage()
    {
        return snapshotStorage;
    }

    public void setSnapshotStorage(SnapshotStorage snapshotStorage)
    {
        this.snapshotStorage = snapshotStorage;
    }

    public DeferredCommandContext getStreamProcessorCmdQueue()
    {
        return streamProcessorCmdContext;
    }

    public void setStreamProcessorCmdQueue(DeferredCommandContext streamProcessorCmdQueue)
    {
        this.streamProcessorCmdContext = streamProcessorCmdQueue;
    }

    public void setEventFilter(EventFilter eventFilter)
    {
        this.eventFilter = eventFilter;
    }

    public EventFilter getEventFilter()
    {
        return eventFilter;
    }

    public void setReprocessingEventFilter(EventFilter reprocessingEventFilter)
    {
        this.reprocessingEventFilter = reprocessingEventFilter;
    }

    public EventFilter getReprocessingEventFilter()
    {
        return reprocessingEventFilter;
    }

    public void setReadOnly(boolean readOnly)
    {
        this.isReadOnlyProcessor = readOnly;
    }

    public boolean isReadOnlyProcessor()
    {
        return isReadOnlyProcessor;
    }

}
