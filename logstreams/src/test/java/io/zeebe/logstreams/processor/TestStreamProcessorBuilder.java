/**
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
import io.zeebe.logstreams.spi.SnapshotPositionProvider;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.util.DeferredCommandContext;
import io.zeebe.util.actor.ActorScheduler;

public class TestStreamProcessorBuilder extends StreamProcessorBuilder
{
    public TestStreamProcessorBuilder(int id, String name, StreamProcessor streamProcessor)
    {
        super(id, name, streamProcessor);
    }

    @Override
    protected void initContext()
    {
        // do nothing
    }

    @Override
    public TestStreamProcessorBuilder sourceStream(LogStream stream)
    {
        return (TestStreamProcessorBuilder) super.sourceStream(stream);
    }

    @Override
    public TestStreamProcessorBuilder targetStream(LogStream stream)
    {
        return (TestStreamProcessorBuilder) super.targetStream(stream);
    }

    @Override
    public TestStreamProcessorBuilder actorScheduler(ActorScheduler actorScheduler)
    {
        return (TestStreamProcessorBuilder) super.actorScheduler(actorScheduler);
    }

    @Override
    public TestStreamProcessorBuilder snapshotPolicy(SnapshotPolicy snapshotPolicy)
    {
        return (TestStreamProcessorBuilder) super.snapshotPolicy(snapshotPolicy);
    }

    @Override
    public TestStreamProcessorBuilder snapshotStorage(SnapshotStorage snapshotStorage)
    {
        return (TestStreamProcessorBuilder) super.snapshotStorage(snapshotStorage);
    }

    @Override
    public TestStreamProcessorBuilder snapshotPositionProvider(SnapshotPositionProvider snapshotPositionProvider)
    {
        return (TestStreamProcessorBuilder) super.snapshotPositionProvider(snapshotPositionProvider);
    }

    @Override
    public TestStreamProcessorBuilder streamProcessorCmdQueue(DeferredCommandContext streamProcessorCmdQueue)
    {
        return (TestStreamProcessorBuilder) super.streamProcessorCmdQueue(streamProcessorCmdQueue);
    }

    @Override
    public TestStreamProcessorBuilder eventFilter(EventFilter eventFilter)
    {
        return (TestStreamProcessorBuilder) super.eventFilter(eventFilter);
    }

    @Override
    public TestStreamProcessorBuilder reprocessingEventFilter(EventFilter eventFilter)
    {
        return (TestStreamProcessorBuilder) super.reprocessingEventFilter(eventFilter);
    }

    @Override
    public TestStreamProcessorBuilder errorHandler(StreamProcessorErrorHandler streamProcessorErrorHandler)
    {
        return (TestStreamProcessorBuilder) super.errorHandler(streamProcessorErrorHandler);
    }

    public TestStreamProcessorBuilder sourceLogStreamReader(LogStreamReader sourceLogStreamReader)
    {
        this.sourceLogStreamReader = sourceLogStreamReader;
        return this;
    }

    public TestStreamProcessorBuilder targetLogStreamReader(LogStreamReader targetLogStreamReader)
    {
        this.targetLogStreamReader = targetLogStreamReader;
        return this;
    }

    public TestStreamProcessorBuilder logStreamWriter(LogStreamWriter logStreamWriter)
    {
        this.logStreamWriter = logStreamWriter;
        return this;
    }

}
