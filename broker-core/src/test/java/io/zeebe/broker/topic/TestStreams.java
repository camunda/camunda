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
package io.zeebe.broker.topic;

import static io.zeebe.test.util.TestUtil.doRepeatedly;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.TopicEvent;
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
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.buffer.BufferUtil;

public class TestStreams
{
    protected static final Map<Class<?>, EventType> EVENT_TYPES = new HashMap<>();

    static
    {
        EVENT_TYPES.put(PartitionEvent.class, EventType.PARTITION_EVENT);
        EVENT_TYPES.put(TopicEvent.class, EventType.TOPIC_EVENT);
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

        logStream.setCommitPosition(Long.MAX_VALUE);
        logStream.open();

        managedLogs.put(name, logStream);
        closeables.manage(logStream);

        return logStream;
    }

    public LogStream getLogStream(String name)
    {
        return managedLogs.get(name);
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

    public StreamProcessorControl runStreamProcessor(String log, StreamProcessor streamProcessor)
    {
        return runStreamProcessor(log, 0, streamProcessor);
    }

    public StreamProcessorControl runStreamProcessor(String log, int streamProcessorId, StreamProcessor streamProcessor)
    {
        final LogStream stream = getLogStream(log);

        final SuspendableStreamProcessor processor = new SuspendableStreamProcessor(streamProcessor);

        final StreamProcessorController streamProcessorController = LogStreams.createStreamProcessor(streamProcessor.toString(), streamProcessorId, processor)
            .sourceStream(stream)
            .targetStream(stream)
            .snapshotStorage(getSnapshotStorage())
            .actorScheduler(actorScheduler)
            .build();

        final StreamProcessorControlImpl control = new StreamProcessorControlImpl(processor, streamProcessorController);

        closeables.manage(control);

        control.start();

        return control;
    }

    protected static class StreamProcessorControlImpl implements StreamProcessorControl, AutoCloseable
    {

        protected final SuspendableStreamProcessor streamProcessor;
        protected final StreamProcessorController controller;

        public StreamProcessorControlImpl(SuspendableStreamProcessor streamProcessor, StreamProcessorController controller)
        {
            this.streamProcessor = streamProcessor;
            this.controller = controller;
        }


        @Override
        public void unblock()
        {
            streamProcessor.resume();
        }

        @Override
        public boolean isBlocked()
        {
            return streamProcessor.isBlocked();
        }

        @Override
        public void blockAfterEvent(Predicate<LoggedEvent> test)
        {
            streamProcessor.blockAfterEvent(test);
        }

        @Override
        public void close()
        {
            try
            {
                controller.closeAsync().get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void start()
        {
            try
            {
                controller.openAsync().get();
            }
            catch (InterruptedException | ExecutionException e)
            {
                throw new RuntimeException(e);
            }

        }

    }

    public static class SuspendableStreamProcessor implements StreamProcessor
    {
        protected final StreamProcessor wrappedProcessor;

        protected AtomicReference<Predicate<LoggedEvent>> blockAfterCondition = new AtomicReference<>(null);
        protected AtomicBoolean suspended = new AtomicBoolean(true);

        protected boolean blockAfterCurrentEvent;

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
            this.suspended.set(false);
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
        public boolean isSuspended()
        {
            if (suspended.get())
            {
                return true;
            }
            else
            {
                return wrappedProcessor.isSuspended();
            }
        }

        public boolean isBlocked()
        {
            return suspended.get();
        }

        @Override
        public void onOpen(StreamProcessorContext context)
        {
            wrappedProcessor.onOpen(context);
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
                suspended.set(true);
            }
        }

        @Override
        public int getPriority(long now)
        {
            return wrappedProcessor.getPriority(now);
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

            writer.raftTermId(logStream.getTerm());
            writer.metadataWriter(metadata);
            writer.valueWriter(value);

            return doRepeatedly(() -> writer.tryWrite()).until(p -> p >= 0);
        }
    }
}
