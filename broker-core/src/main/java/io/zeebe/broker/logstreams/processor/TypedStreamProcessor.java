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
package io.zeebe.broker.logstreams.processor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.ReflectUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TypedStreamProcessor implements StreamProcessor
{

    protected final SnapshotSupport snapshotSupport;
    protected final ServerOutput output;
    protected final EnumMap<EventType, EnumMap> eventProcessors;
    protected final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();

    protected final BrokerEventMetadata metadata = new BrokerEventMetadata();
    protected final EnumMap<EventType, Class<? extends UnpackedObject>> eventRegistry;
    protected final EnumMap<EventType, UnpackedObject> eventCache;

    protected final TypedEventImpl typedEvent = new TypedEventImpl();
    protected DelegatingEventProcessor eventProcessorWrapper;
    protected ActorControl actor;
    private StreamProcessorContext streamProcessorContext;

    public TypedStreamProcessor(
            SnapshotSupport snapshotSupport,
            ServerOutput output,
            EnumMap<EventType, EnumMap> eventProcessors,
            List<StreamProcessorLifecycleAware> lifecycleListeners,
            EnumMap<EventType, Class<? extends UnpackedObject>> eventRegistry)
    {
        this.snapshotSupport = snapshotSupport;
        this.output = output;
        this.eventProcessors = eventProcessors;
        eventProcessors.values().forEach(p -> this.lifecycleListeners.addAll(p.values()));
        this.lifecycleListeners.addAll(lifecycleListeners);

        this.eventCache = new EnumMap<>(EventType.class);

        eventRegistry.forEach((t, c) -> eventCache.put(t, ReflectUtil.newInstance(c)));
        this.eventRegistry = eventRegistry;
    }

    @Override
    public void onOpen(StreamProcessorContext context)
    {
        this.eventProcessorWrapper = new DelegatingEventProcessor(
                context.getId(),
                output,
                context.getLogStream(),
                eventRegistry);

        this.actor = context.getActorControl();
        this.streamProcessorContext = context;
        lifecycleListeners.forEach(e -> e.onOpen(this));
    }

    @Override
    public void onClose()
    {
        lifecycleListeners.forEach(e -> e.onClose());
    }

    @Override
    public SnapshotSupport getStateResource()
    {
        return snapshotSupport;
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event)
    {
        metadata.reset();
        event.readMetadata(metadata);

        final EnumMap processorsForType = eventProcessors.get(metadata.getEventType());
        if (processorsForType == null || processorsForType.isEmpty())
        {
            return null;
        }

        final UnpackedObject value = eventCache.get(metadata.getEventType());
        value.reset();
        event.readValue(value);

        final Enum state = getEventState(value);
        final TypedEventProcessor currentProcessor = (TypedEventProcessor) processorsForType.get(state);

        if (currentProcessor != null)
        {
            typedEvent.wrap(event, metadata, value);
            eventProcessorWrapper.wrap(currentProcessor, typedEvent);
            return eventProcessorWrapper;
        }
        else
        {
            return null;
        }
    }

    public MetadataFilter buildTypeFilter()
    {
        return m -> eventProcessors.containsKey(m.getEventType());
    }

    public ActorFuture<Void> runAsync(Runnable runnable)
    {
        return actor.call(runnable);
    }

    // TODO: this goes away when we move the state into the event header => https://github.com/zeebe-io/zeebe/issues/367
    protected Enum getEventState(UnpackedObject value)
    {
        if (value instanceof TopicEvent)
        {
            return ((TopicEvent) value).getState();
        }
        else if (value instanceof PartitionEvent)
        {
            return ((PartitionEvent) value).getState();
        }
        else if (value instanceof DeploymentEvent)
        {
            return ((DeploymentEvent) value).getState();
        }
        else if (value instanceof WorkflowEvent)
        {
            return ((WorkflowEvent) value).getState();
        }
        else if (value instanceof TaskEvent)
        {
            return ((TaskEvent) value).getState();
        }
        else
        {
            throw new RuntimeException("event type " + value.getClass() + " not supported");
        }
    }

    protected static class DelegatingEventProcessor implements EventProcessor
    {

        protected final int streamProcessorId;
        protected final LogStream logStream;
        protected final TypedStreamWriterImpl writer;
        protected final TypedResponseWriterImpl responseWriter;

        protected TypedEventProcessor eventProcessor;
        protected TypedEventImpl event;

        public DelegatingEventProcessor(
                int streamProcessorId,
                ServerOutput output,
                LogStream logStream,
                EnumMap<EventType, Class<? extends UnpackedObject>> eventRegistry)
        {
            this.streamProcessorId = streamProcessorId;
            this.logStream = logStream;
            this.writer =  new TypedStreamWriterImpl(logStream, eventRegistry);
            this.responseWriter = new TypedResponseWriterImpl(output, logStream.getPartitionId());
        }

        public void wrap(TypedEventProcessor eventProcessor, TypedEventImpl event)
        {
            this.eventProcessor = eventProcessor;
            this.event = event;
        }

        @Override
        public void processEvent()
        {
            eventProcessor.processEvent(event);
        }

        @Override
        public boolean executeSideEffects()
        {
            return eventProcessor.executeSideEffects(event, responseWriter);
        }

        @Override
        public long writeEvent(LogStreamWriter writer)
        {
            this.writer.configureSourceContext(streamProcessorId, logStream.getPartitionId(), event.getPosition());
            return eventProcessor.writeEvent(event, this.writer);
        }

        @Override
        public void updateState()
        {
            eventProcessor.updateState(event);
        }

    }

    public ActorControl getActor()
    {
        return actor;
    }

    public StreamProcessorContext getStreamProcessorContext()
    {
        return streamProcessorContext;
    }
}
