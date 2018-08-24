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

import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.state.StateStorageFactory;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.topic.Records;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.impl.service.StreamProcessorService;
import io.zeebe.logstreams.impl.snapshot.fs.FsSnapshotController;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.logstreams.spi.SnapshotSupport;
import io.zeebe.logstreams.state.StateController;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.LangUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class TestStreams {
  protected static final Map<Class<?>, ValueType> VALUE_TYPES = new HashMap<>();

  static {
    VALUE_TYPES.put(DeploymentRecord.class, ValueType.DEPLOYMENT);
    VALUE_TYPES.put(IncidentRecord.class, ValueType.INCIDENT);
    VALUE_TYPES.put(JobRecord.class, ValueType.JOB);
    VALUE_TYPES.put(TopicRecord.class, ValueType.TOPIC);
    VALUE_TYPES.put(WorkflowInstanceRecord.class, ValueType.WORKFLOW_INSTANCE);
    VALUE_TYPES.put(MessageRecord.class, ValueType.MESSAGE);
    VALUE_TYPES.put(MessageSubscriptionRecord.class, ValueType.MESSAGE_SUBSCRIPTION);
    VALUE_TYPES.put(
        WorkflowInstanceSubscriptionRecord.class, ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);

    VALUE_TYPES.put(UnpackedObject.class, ValueType.NOOP);
  }

  protected final File storageDirectory;
  protected final AutoCloseableRule closeables;
  private final ServiceContainer serviceContainer;

  protected Map<String, LogStream> managedLogs = new HashMap<>();

  protected ActorScheduler actorScheduler;

  protected StateStorageFactory stateStorageFactory;

  protected SnapshotStorage snapshotStorage;

  protected StateStorage stateStorage;

  public TestStreams(
      File storageDirectory,
      AutoCloseableRule closeables,
      ServiceContainer serviceContainer,
      ActorScheduler actorScheduler) {
    this.storageDirectory = storageDirectory;
    this.closeables = closeables;
    this.serviceContainer = serviceContainer;
    this.actorScheduler = actorScheduler;
  }

  public LogStream createLogStream(String name) {
    return createLogStream(name, 0);
  }

  public LogStream createLogStream(String name, int partitionId) {
    final String rootPath = storageDirectory.getAbsolutePath();
    final LogStream logStream =
        LogStreams.createFsLogStream(BufferUtil.wrapString(name), partitionId)
            .logRootPath(rootPath)
            .serviceContainer(serviceContainer)
            .logName(name)
            .deleteOnClose(true)
            .build()
            .join();

    actorScheduler
        .submitActor(
            new Actor() {
              @Override
              protected void onActorStarting() {
                final ActorCondition condition =
                    actor.onCondition(
                        "on-append", () -> logStream.setCommitPosition(Long.MAX_VALUE));
                logStream.registerOnAppendCondition(condition);
              }
            })
        .join();

    logStream.openAppender().join();

    managedLogs.put(name, logStream);
    closeables.manage(logStream);

    return logStream;
  }

  public LogStream getLogStream(String name) {
    return managedLogs.get(name);
  }

  /**
   * Truncates events with position greater than the argument. Includes committed events. Resets
   * commit position to the argument position.
   *
   * @param position exclusive (unlike {@link LogStream#truncate(long)}!)
   */
  public void truncate(String stream, long position) {
    final LogStream logStream = getLogStream(stream);
    try (LogStreamReader reader = new BufferedLogStreamReader(logStream)) {
      logStream.closeAppender().get();

      reader.seek(position + 1);

      logStream.setCommitPosition(position);
      if (reader.hasNext()) {
        logStream.truncate(reader.next().getPosition());
      }
      logStream.setCommitPosition(Long.MAX_VALUE);

      logStream.openAppender().get();
    } catch (Exception e) {
      throw new RuntimeException("Could not truncate log stream " + stream, e);
    }
  }

  public Stream<LoggedEvent> events(String logName) {
    final LogStream logStream = managedLogs.get(logName);

    final LogStreamReader reader = new BufferedLogStreamReader(logStream);
    closeables.manage(reader);

    reader.seekToFirstEvent();

    final Iterable<LoggedEvent> iterable = () -> reader;

    return StreamSupport.stream(iterable.spliterator(), false);
  }

  public FluentLogWriter newRecord(String logName) {
    final LogStream logStream = getLogStream(logName);
    return new FluentLogWriter(logStream);
  }

  protected SnapshotStorage getSnapshotStorage() {
    if (snapshotStorage == null) {
      snapshotStorage =
          LogStreams.createFsSnapshotStore(storageDirectory.getAbsolutePath()).build();
    }
    return snapshotStorage;
  }

  protected StateStorageFactory getStateStorageFactory() {
    if (stateStorageFactory == null) {
      final File rocksDBDirectory = new File(storageDirectory, "state");
      if (!rocksDBDirectory.exists()) {
        rocksDBDirectory.mkdir();
      }

      stateStorageFactory = new StateStorageFactory(rocksDBDirectory);
    }

    return stateStorageFactory;
  }

  public StreamProcessorControl initStreamProcessor(String log, StreamProcessor streamProcessor) {
    return initStreamProcessor(log, 0, () -> streamProcessor);
  }

  public StreamProcessorControl initStreamProcessor(
      String log, int streamProcessorId, Supplier<StreamProcessor> factory) {
    final LogStream stream = getLogStream(log);

    final StreamProcessorControlImpl control =
        new StreamProcessorControlImpl(stream, factory, streamProcessorId);

    closeables.manage(control);

    return control;
  }

  protected class StreamProcessorControlImpl implements StreamProcessorControl, AutoCloseable {

    private final Supplier<StreamProcessor> factory;
    private final int streamProcessorId;
    private final LogStream stream;

    protected SuspendableStreamProcessor currentStreamProcessor;
    protected StreamProcessorController currentController;
    protected StreamProcessorService currentStreamProcessorService;
    protected SnapshotController currentSnapshotController;

    public StreamProcessorControlImpl(
        LogStream stream, Supplier<StreamProcessor> factory, int streamProcessorId) {
      this.stream = stream;
      this.factory = factory;
      this.streamProcessorId = streamProcessorId;
    }

    @Override
    public void purgeSnapshot() {
      try {
        currentSnapshotController.purgeAll();
      } catch (Exception e) {
        LangUtil.rethrowUnchecked(e);
      }
    }

    @Override
    public void unblock() {
      currentStreamProcessor.resume();
    }

    @Override
    public boolean isBlocked() {
      return currentController.isSuspended();
    }

    @Override
    public void blockAfterEvent(Predicate<LoggedEvent> test) {
      ensureStreamProcessorBuilt();
      currentStreamProcessor.blockAfterEvent(test);
    }

    @Override
    public void blockAfterJobEvent(Predicate<TypedRecord<JobRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isJobRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, JobRecord.class)));
    }

    @Override
    public void blockAfterDeploymentEvent(Predicate<TypedRecord<DeploymentRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isDeploymentRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, DeploymentRecord.class)));
    }

    @Override
    public void blockAfterWorkflowInstanceRecord(
        Predicate<TypedRecord<WorkflowInstanceRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isWorkflowInstanceRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, WorkflowInstanceRecord.class)));
    }

    @Override
    public void blockAfterTopicEvent(Predicate<TypedRecord<TopicRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isTopicRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, TopicRecord.class)));
    }

    @Override
    public void blockAfterIncidentEvent(Predicate<TypedRecord<IncidentRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isIncidentRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, IncidentRecord.class)));
    }

    @Override
    public void blockAfterMessageEvent(Predicate<TypedRecord<MessageRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isMessageRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, MessageRecord.class)));
    }

    @Override
    public void blockAfterMessageSubscriptionEvent(
        Predicate<TypedRecord<MessageSubscriptionRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isMessageSubscriptionRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, MessageSubscriptionRecord.class)));
    }

    @Override
    public void blockAfterWorkflowInstanceSubscriptionEvent(
        Predicate<TypedRecord<WorkflowInstanceSubscriptionRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isWorkflowInstanceSubscriptionRecord(e)
                  && test.test(
                      CopiedTypedEvent.toTypedEvent(e, WorkflowInstanceSubscriptionRecord.class)));
    }

    @Override
    public void close() {
      if (currentController != null && currentController.isOpened()) {
        currentStreamProcessorService.close();
      }

      currentStreamProcessorService = null;
      currentController = null;
      currentStreamProcessor = null;
    }

    @Override
    public void start() {
      currentStreamProcessorService = buildStreamProcessorController();
      currentController = currentStreamProcessorService.getController();
    }

    @Override
    public void restart() {
      close();
      start();
    }

    private void ensureStreamProcessorBuilt() {
      if (currentStreamProcessor == null) {
        final StreamProcessor processor = factory.get();
        currentStreamProcessor = new SuspendableStreamProcessor(processor);
      }
    }

    private StreamProcessorService buildStreamProcessorController() {
      ensureStreamProcessorBuilt();

      // stream processor names need to be unique for snapshots to work properly
      // using the class name assumes that one stream processor class is not instantiated more than
      // once in a test
      final String name = currentStreamProcessor.wrappedProcessor.getClass().getSimpleName();

      if (currentStreamProcessor.getStateController() != null) {
        final StateStorage stateStorage = getStateStorageFactory().create(streamProcessorId, name);
        currentSnapshotController =
            new StateSnapshotController(currentStreamProcessor.getStateController(), stateStorage);
      } else {
        currentSnapshotController =
            new FsSnapshotController(
                getSnapshotStorage(), name, currentStreamProcessor.getStateResource());
      }

      return LogStreams.createStreamProcessor(name, streamProcessorId, currentStreamProcessor)
          .logStream(stream)
          .snapshotController(currentSnapshotController)
          .actorScheduler(actorScheduler)
          .serviceContainer(serviceContainer)
          .build()
          .join();
    }
  }

  public static class SuspendableStreamProcessor implements StreamProcessor {
    protected final StreamProcessor wrappedProcessor;

    protected AtomicReference<Predicate<LoggedEvent>> blockAfterCondition =
        new AtomicReference<>(null);

    protected boolean blockAfterCurrentEvent;
    private StreamProcessorContext context;

    public SuspendableStreamProcessor(StreamProcessor wrappedProcessor) {
      this.wrappedProcessor = wrappedProcessor;
    }

    @Override
    public SnapshotSupport getStateResource() {
      return wrappedProcessor.getStateResource();
    }

    @Override
    public StateController getStateController() {
      return wrappedProcessor.getStateController();
    }

    public void resume() {
      context
          .getActorControl()
          .call(
              () -> {
                context.resumeController();
              });
    }

    public void blockAfterEvent(Predicate<LoggedEvent> test) {
      this.blockAfterCondition.set(test);
    }

    @Override
    public EventProcessor onEvent(LoggedEvent event) {
      final Predicate<LoggedEvent> suspensionCondition = this.blockAfterCondition.get();
      blockAfterCurrentEvent = suspensionCondition != null && suspensionCondition.test(event);

      final EventProcessor actualProcessor = wrappedProcessor.onEvent(event);

      return new EventProcessor() {

        @Override
        public void processEvent(EventLifecycleContext ctx) {
          if (actualProcessor != null) {
            actualProcessor.processEvent(ctx);
          }
        }

        @Override
        public boolean executeSideEffects() {
          return actualProcessor != null ? actualProcessor.executeSideEffects() : true;
        }

        @Override
        public long writeEvent(LogStreamRecordWriter writer) {
          return actualProcessor != null ? actualProcessor.writeEvent(writer) : 0;
        }

        @Override
        public void updateState() {
          if (actualProcessor != null) {
            actualProcessor.updateState();
          }

          if (blockAfterCurrentEvent) {
            blockAfterCurrentEvent = false;
            context.suspendController();
          }
        }
      };
    }

    @Override
    public void onOpen(StreamProcessorContext context) {
      this.context = context;
      wrappedProcessor.onOpen(this.context);
    }

    @Override
    public void onClose() {
      wrappedProcessor.onClose();
    }
  }

  public static class FluentLogWriter {

    protected RecordMetadata metadata = new RecordMetadata();
    protected UnpackedObject value;
    protected LogStream logStream;
    protected long key = -1;

    public FluentLogWriter(LogStream logStream) {
      this.logStream = logStream;

      metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
    }

    public FluentLogWriter metadata(Consumer<RecordMetadata> metadata) {
      metadata.accept(this.metadata);
      return this;
    }

    public FluentLogWriter intent(Intent intent) {
      this.metadata.intent(intent);
      return this;
    }

    public FluentLogWriter recordType(RecordType recordType) {
      this.metadata.recordType(recordType);
      return this;
    }

    public TestStreams.FluentLogWriter key(long key) {
      this.key = key;
      return this;
    }

    public TestStreams.FluentLogWriter event(UnpackedObject event) {
      final ValueType eventType = VALUE_TYPES.get(event.getClass());
      if (eventType == null) {
        throw new RuntimeException("No event type registered for value " + event.getClass());
      }

      this.metadata.valueType(eventType);
      this.value = event;
      return this;
    }

    public long write() {
      final LogStreamRecordWriter writer = new LogStreamWriterImpl(logStream);

      if (key >= 0) {
        writer.key(key);
      } else {
        writer.positionAsKey();
      }

      writer.metadataWriter(metadata);
      writer.valueWriter(value);

      return doRepeatedly(() -> writer.tryWrite()).until(p -> p >= 0);
    }
  }
}
