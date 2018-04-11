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

import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.log.*;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;

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

    protected boolean readOnly;

    protected ServiceContainer serviceContainer;

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

    public StreamProcessorBuilder serviceContainer(ServiceContainer serviceContainer)
    {
        this.serviceContainer = serviceContainer;
        return this;
    }

    public ActorFuture<StreamProcessorService> build()
    {
        validate();

        final StreamProcessorContext context = createContext();
        final StreamProcessorController controller = new StreamProcessorController(context);

        final String logName = logStream.getLogName();

        final ServiceName<StreamProcessorService> serviceName = LogStreamServiceNames.streamProcessorService(logName, name);
        final StreamProcessorService service = new StreamProcessorService(controller, serviceContainer, serviceName);
        return serviceContainer.createService(serviceName, service)
            .dependency(LogStreamServiceNames.logStreamServiceName(logName))
            .installAndReturn();
    }

    private void validate()
    {
        Objects.requireNonNull(streamProcessor, "No stream processor provided.");
        Objects.requireNonNull(logStream, "No log stream provided.");
        Objects.requireNonNull(actorScheduler, "No task scheduler provided.");
        Objects.requireNonNull(snapshotStorage, "No snapshot storage provided.");
        Objects.requireNonNull(serviceContainer, "No service container provided.");
    }

    private StreamProcessorContext createContext()
    {
        final StreamProcessorContext ctx = new StreamProcessorContext();
        ctx.setId(id);
        ctx.setName(name);
        ctx.setStreamProcessor(streamProcessor);

        ctx.setLogStream(logStream);

        ctx.setActorScheduler(actorScheduler);

        ctx.setSnapshotStorage(snapshotStorage);

        ctx.setEventFilter(eventFilter);
        ctx.setReadOnly(readOnly);

        if (snapshotPeriod == null)
        {
            snapshotPeriod = Duration.ofMinutes(1);
        }
        ctx.setSnapshotPeriod(snapshotPeriod);

        logStreamReader = new BufferedLogStreamReader();
        ctx.setLogStreamReader(logStreamReader);

        if (readOnly)
        {
            logStreamWriter = new DisabledLogStreamWriter();
        }
        else
        {
            logStreamWriter = new LogStreamWriterImpl();
        }
        ctx.setLogStreamWriter(logStreamWriter);

        return ctx;
    }

}
