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
package io.zeebe.broker.util;

import static io.zeebe.test.util.TestUtil.doRepeatedly;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.zeebe.broker.incident.data.IncidentEvent;
import io.zeebe.broker.logstreams.processor.TypedEvent;
import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.topic.Events;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.WorkflowEvent;
import io.zeebe.broker.workflow.data.WorkflowInstanceEvent;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;

public class TestStreams
{
    protected static final Map<Class<?>, EventType> EVENT_TYPES = new HashMap<>();

    static
    {
        EVENT_TYPES.put(DeploymentEvent.class, EventType.DEPLOYMENT_EVENT);
        EVENT_TYPES.put(IncidentEvent.class, EventType.INCIDENT_EVENT);
        EVENT_TYPES.put(PartitionEvent.class, EventType.PARTITION_EVENT);
        EVENT_TYPES.put(TaskEvent.class, EventType.TASK_EVENT);
        EVENT_TYPES.put(TopicEvent.class, EventType.TOPIC_EVENT);
        EVENT_TYPES.put(WorkflowEvent.class, EventType.WORKFLOW_EVENT);
        EVENT_TYPES.put(WorkflowInstanceEvent.class, EventType.WORKFLOW_INSTANCE_EVENT);

        EVENT_TYPES.put(UnpackedObject.class, EventType.NOOP_EVENT);
    }

    protected final File storageDirectory;
    protected final AutoCloseableRule closeables;

    protected Map<String, LogStream> managedLogs = new HashMap<>();

    protected ActorScheduler actorScheduler;

    protected SnapshotStorage snapshotStorage;

    public TestStreams(
        File storageDirectory,
        AutoCloseableRule closeables,
        ActorScheduler actorScheduler)
    {
        this.storageDirectory = storageDirectory;
        this.closeables = closeables;
        this.actorScheduler = actorScheduler;
    }

    public LogStream createLogStream(String name)
    {
        final String rootPath = storageDirectory.getAbsolutePath();
        final LogStream logStream = LogStreams.createFsLogStream(BufferUtil.wrapString(name), 0)
            .logRootPath(rootPath)
            .actorScheduler(actorScheduler)
            .deleteOnClose(true)
            .build();

        logStream.open();
        logStream.openLogStreamController().join();

        actorScheduler.submitActor(new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                final ActorCondition condition = actor.onCondition("on-append", () -> logStream.setCommitPosition(Long.MAX_VALUE));
                logStream.registerOnAppendCondition(condition);
            }
        });

        managedLogs.put(name, logStream);
        closeables.manage(logStream);

        return logStream;
    }

    public LogStream getLogStream(String name)
    {
        return managedLogs.get(name);
    }

    /**
     * Truncates events with position greater than the argument. Includes committed events.
     * Resets commit position to the argument position.
     *
     * @param position exclusive (unlike {@link LogStream#truncate(long)}!)
     */
    public void truncate(String stream, long position)
    {
        final LogStream logStream = getLogStream(stream);
        try (LogStreamReader reader = new BufferedLogStreamReader(logStream))
        {
            logStream.closeLogStreamController().get();

            reader.seek(position + 1);

            logStream.setCommitPosition(position);
            if (reader.hasNext())
            {
                logStream.truncate(reader.next().getPosition());
            }
            logStream.setCommitPosition(Long.MAX_VALUE);

            logStream.openLogStreamController().get();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Could not truncate log stream " + stream, e);
        }
    }

    public Stream<LoggedEvent> events(String logName)
    {
        final LogStream logStream = managedLogs.get(logName);
        final LogStreamReader reader = new BufferedLogStreamReader(logStream);
        reader.seekToFirstEvent();

        final Iterable<LoggedEvent> iterable = () -> reader;

        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public FluentLogWriter newEvent(String logName)
    {
        final LogStream logStream = getLogStream(logName);
        return new FluentLogWriter(logStream);
    }

    protected SnapshotStorage getSnapshotStorage()
    {
        if (snapshotStorage == null)
        {
            snapshotStorage = LogStreams.createFsSnapshotStore(storageDirectory.getAbsolutePath()).build();
        }
        return snapshotStorage;

    }

    public StreamProcessorControl initStreamProcessor(String log, StreamProcessor streamProcessor)
    {
        return initStreamProcessor(log, 0, () -> streamProcessor);
    }

    public StreamProcessorControl initStreamProcessor(String log, int streamProcessorId, Supplier<StreamProcessor> factory)
    {
        final LogStream stream = getLogStream(log);

        final StreamProcessorControlImpl control = new StreamProcessorControlImpl(
                stream,
                factory,
                streamProcessorId);

        closeables.manage(control);

        return control;
    }

    protected class StreamProcessorControlImpl implements StreamProcessorControl, AutoCloseable
    {

        private final Supplier<StreamProcessor> factory;
        private final int streamProcessorId;
        private final LogStream stream;

        protected SuspendableStreamProcessor currentStreamProcessor;
        protected StreamProcessorController currentController;
        private Consumer<SnapshotStorage> snapshotCleaner;

        public StreamProcessorControlImpl(
                LogStream stream,
                Supplier<StreamProcessor> factory,
                int streamProcessorId)
        {
            this.stream = stream;
            this.factory = factory;
            this.streamProcessorId = streamProcessorId;
        }

        @Override
        public void purgeSnapshot()
        {
            snapshotCleaner.accept(snapshotStorage);
        }

        @Override
        public void unblock()
        {
            currentStreamProcessor.resume();
        }

        @Override
        public boolean isBlocked()
        {
            return currentController.isSuspended();
        }

        @Override
        public void blockAfterEvent(Predicate<LoggedEvent> test)
        {
            ensureStreamProcessorBuilt();
            currentStreamProcessor.blockAfterEvent(test);
        }

        @Override
        public void blockAfterTaskEvent(Predicate<TypedEvent<TaskEvent>> test)
        {
            blockAfterEvent(e -> Events.isTaskEvent(e) && test.test(CopiedTypedEvent.toTypedEvent(e, TaskEvent.class)));
        }

        @Override
        public void blockAfterDeploymentEvent(Predicate<TypedEvent<DeploymentEvent>> test)
        {
            blockAfterEvent(e -> Events.isDeploymentEvent(e) && test.test(CopiedTypedEvent.toTypedEvent(e, DeploymentEvent.class)));
        }

        @Override
        public void blockAfterTopicEvent(Predicate<TypedEvent<TopicEvent>> test)
        {
            blockAfterEvent(e -> Events.isTopicEvent(e) && test.test(CopiedTypedEvent.toTypedEvent(e, TopicEvent.class)));
        }

        @Override
        public void blockAfterPartitionEvent(Predicate<TypedEvent<PartitionEvent>> test)
        {
            blockAfterEvent(e -> Events.isPartitionEvent(e) && test.test(CopiedTypedEvent.toTypedEvent(e, PartitionEvent.class)));
        }

        @Override
        public void blockAfterIncidentEvent(Predicate<TypedEvent<IncidentEvent>> test)
        {
            blockAfterEvent(e -> Events.isIncidentEvent(e) && test.test(CopiedTypedEvent.toTypedEvent(e, IncidentEvent.class)));
        }

        @Override
        public void close()
        {
            if (currentController != null && currentController.isOpened())
            {
                try
                {
                    currentController.closeAsync().get();
                }
                catch (InterruptedException | ExecutionException e)
                {
                    throw new RuntimeException(e);
                }
            }

            currentController = null;
            currentStreamProcessor = null;
        }

        @Override
        public void start()
        {
            currentController = buildStreamProcessorController();
            final String controllerName = currentController.getName();
            snapshotCleaner = storage -> storage.purgeSnapshot(controllerName);

            try
            {
                currentController.openAsync().get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void restart()
        {
            close();
            start();
        }

        private void ensureStreamProcessorBuilt()
        {
            if (currentStreamProcessor == null)
            {
                final StreamProcessor processor = factory.get();
                currentStreamProcessor = new SuspendableStreamProcessor(processor);
            }
        }

        private StreamProcessorController buildStreamProcessorController()
        {
            ensureStreamProcessorBuilt();

            // stream processor names need to be unique for snapshots to work properly
            // using the class name assumes that one stream processor class is not instantiated more than once in a test
            final String name = currentStreamProcessor.wrappedProcessor.getClass().getSimpleName();

            return LogStreams.createStreamProcessor(name, streamProcessorId, currentStreamProcessor)
                .logStream(stream)
                .snapshotStorage(getSnapshotStorage())
                .actorScheduler(actorScheduler)
                .build();
        }

    }

    public static class SuspendableStreamProcessor implements StreamProcessor
    {
        protected final StreamProcessor wrappedProcessor;

        protected AtomicReference<Predicate<LoggedEvent>> blockAfterCondition = new AtomicReference<>(null);

        protected boolean blockAfterCurrentEvent;
        private StreamProcessorContext context;

        public SuspendableStreamProcessor(StreamProcessor wrappedProcessor)
        {
            this.wrappedProcessor = wrappedProcessor;
        }

        @Override
        public SnapshotSupport getStateResource()
        {
            return wrappedProcessor.getStateResource();
        }

        public void resume()
        {
            context.getActorControl().call(() ->
            {
                context.resumeController();
            });
        }

        public void blockAfterEvent(Predicate<LoggedEvent> test)
        {
            this.blockAfterCondition.set(test);
        }

        @Override
        public EventProcessor onEvent(LoggedEvent event)
        {
            final Predicate<LoggedEvent> suspensionCondition = this.blockAfterCondition.get();
            blockAfterCurrentEvent = suspensionCondition != null && suspensionCondition.test(event);

            final EventProcessor actualProcessor = wrappedProcessor.onEvent(event);

            return new EventProcessor()
            {

                @Override
                public void processEvent()
                {
                    if (actualProcessor != null)
                    {
                        actualProcessor.processEvent();
                    }
                }

                @Override
                public boolean executeSideEffects()
                {
                    return actualProcessor != null ? actualProcessor.executeSideEffects() : true;
                }

                @Override
                public long writeEvent(LogStreamWriter writer)
                {
                    return actualProcessor != null ? actualProcessor.writeEvent(writer) : 0;
                }

                @Override
                public void updateState()
                {
                    if (actualProcessor != null)
                    {
                        actualProcessor.updateState();
                    }
                }
            };
        }

        @Override
        public void onOpen(StreamProcessorContext context)
        {
            this.context = context;
            wrappedProcessor.onOpen(this.context);
        }

        @Override
        public void onClose()
        {
            wrappedProcessor.onClose();
        }

        @Override
        public void afterEvent()
        {
            if (blockAfterCurrentEvent)
            {
                blockAfterCurrentEvent = false;
                context.suspendController();
            }
        }

    }

    public static class FluentLogWriter
    {

        protected BrokerEventMetadata metadata = new BrokerEventMetadata();
        protected UnpackedObject value;
        protected LogStream logStream;
        protected long key = -1;

        public FluentLogWriter(LogStream logStream)
        {
            this.logStream = logStream;

            metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
        }

        public FluentLogWriter metadata(Consumer<BrokerEventMetadata> metadata)
        {
            metadata.accept(this.metadata);
            return this;
        }

        public TestStreams.FluentLogWriter key(long key)
        {
            this.key = key;
            return this;
        }

        public TestStreams.FluentLogWriter event(UnpackedObject event)
        {
            final EventType eventType = EVENT_TYPES.get(event.getClass());
            if (eventType == null)
            {
                throw new RuntimeException("No event type registered for value " + event.getClass());
            }

            this.metadata.eventType(eventType);
            this.value = event;
            return this;
        }

        public long write()
        {
            final LogStreamWriter writer = new LogStreamWriterImpl(logStream);

            if (key >= 0)
            {
                writer.key(key);
            }
            else
            {
                writer.positionAsKey();
            }

            writer.metadataWriter(metadata);
            writer.valueWriter(value);

            return doRepeatedly(() -> writer.tryWrite()).until(p -> p >= 0);
        }
    }
}
