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
import io.zeebe.logstreams.spi.*;
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
    public TestStreamProcessorBuilder logStream(LogStream stream)
    {
        return (TestStreamProcessorBuilder) super.logStream(stream);
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

    public TestStreamProcessorBuilder logStreamReader(LogStreamReader logStreamReader)
    {
        this.logStreamReader = logStreamReader;
        return this;
    }

    public TestStreamProcessorBuilder logStreamWriter(LogStreamWriter logStreamWriter)
    {
        this.logStreamWriter = logStreamWriter;
        return this;
    }

}
