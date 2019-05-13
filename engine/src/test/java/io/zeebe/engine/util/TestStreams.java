/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.util;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.distributedlog.impl.DistributedLogstreamServiceConfig;
import io.zeebe.engine.processor.EventProcessor;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.StreamProcessorContext;
import io.zeebe.engine.processor.StreamProcessorController;
import io.zeebe.engine.processor.StreamProcessorFactory;
import io.zeebe.engine.processor.StreamProcessorService;
import io.zeebe.engine.processor.StreamProcessors;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.rules.TemporaryFolder;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.stubbing.Answer;

public class TestStreams {
  protected static final Map<Class<?>, ValueType> VALUE_TYPES = new HashMap<>();

  static {
    VALUE_TYPES.put(DeploymentRecord.class, ValueType.DEPLOYMENT);
    VALUE_TYPES.put(IncidentRecord.class, ValueType.INCIDENT);
    VALUE_TYPES.put(JobRecord.class, ValueType.JOB);
    VALUE_TYPES.put(WorkflowInstanceRecord.class, ValueType.WORKFLOW_INSTANCE);
    VALUE_TYPES.put(MessageRecord.class, ValueType.MESSAGE);
    VALUE_TYPES.put(MessageSubscriptionRecord.class, ValueType.MESSAGE_SUBSCRIPTION);
    VALUE_TYPES.put(
        MessageStartEventSubscriptionRecord.class, ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
    VALUE_TYPES.put(
        WorkflowInstanceSubscriptionRecord.class, ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    VALUE_TYPES.put(JobBatchRecord.class, ValueType.JOB_BATCH);
    VALUE_TYPES.put(TimerRecord.class, ValueType.TIMER);
    VALUE_TYPES.put(VariableRecord.class, ValueType.VARIABLE);
    VALUE_TYPES.put(VariableDocumentRecord.class, ValueType.VARIABLE_DOCUMENT);
    VALUE_TYPES.put(WorkflowInstanceCreationRecord.class, ValueType.WORKFLOW_INSTANCE_CREATION);
    VALUE_TYPES.put(ErrorRecord.class, ValueType.ERROR);

    VALUE_TYPES.put(UnpackedObject.class, ValueType.NOOP);
  }

  protected final TemporaryFolder storageDirectory;
  protected final AutoCloseableRule closeables;
  private final ServiceContainer serviceContainer;

  protected Map<String, LogStream> managedLogs = new HashMap<>();

  protected ActorScheduler actorScheduler;

  protected StateStorageFactory stateStorageFactory;

  public TestStreams(
      final TemporaryFolder storageDirectory,
      final AutoCloseableRule closeables,
      final ServiceContainer serviceContainer,
      final ActorScheduler actorScheduler) {
    this.storageDirectory = storageDirectory;
    this.closeables = closeables;
    this.serviceContainer = serviceContainer;
    this.actorScheduler = actorScheduler;
  }

  public LogStream createLogStream(final String name) {
    return createLogStream(name, 0);
  }

  public LogStream createLogStream(final String name, final int partitionId) {
    File segments = null, index = null, snapshots = null;

    try {
      segments = storageDirectory.newFolder("segments");
      index = storageDirectory.newFolder("index", "runtime");
      snapshots = storageDirectory.newFolder("index", "snapshots");
    } catch (IOException e) {
      e.printStackTrace();
    }

    final StateStorage stateStorage = new StateStorage(index, snapshots);

    final LogStream logStream =
        LogStreams.createFsLogStream(partitionId)
            .logRootPath(segments.getAbsolutePath())
            .serviceContainer(serviceContainer)
            .logName(name)
            .deleteOnClose(true)
            .indexStateStorage(stateStorage)
            .build()
            .join();

    // Create distributed log service
    final DistributedLogstreamPartition mockDistLog = mock(DistributedLogstreamPartition.class);

    final DistributedLogstreamService distributedLogImpl =
        new DefaultDistributedLogstreamService(new DistributedLogstreamServiceConfig());

    // initialize private members
    final String nodeId = "0";
    try {
      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStream"),
          logStream);

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStorage"),
          logStream.getLogStorage());

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("currentLeader"),
          nodeId);
    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    }

    // mock append
    doAnswer(
            (Answer<CompletableFuture<Long>>)
                invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length > 1
                      && arguments[0] != null
                      && arguments[1] != null) {
                    final byte[] bytes = (byte[]) arguments[0];
                    final long pos = (long) arguments[1];
                    return CompletableFuture.completedFuture(
                        distributedLogImpl.append(nodeId, pos, bytes));
                  }
                  return null;
                })
        .when(mockDistLog)
        .asyncAppend(any(byte[].class), anyLong());

    serviceContainer
        .createService(distributedLogPartitionServiceName(name), () -> mockDistLog)
        .install()
        .join();

    logStream.openAppender().join();

    managedLogs.put(name, logStream);
    closeables.manage(logStream);

    return logStream;
  }

  public LogStream getLogStream(final String name) {
    return managedLogs.get(name);
  }

  public Stream<LoggedEvent> events(final String logName) {
    final LogStream logStream = managedLogs.get(logName);

    final LogStreamReader reader = new BufferedLogStreamReader(logStream);
    closeables.manage(reader);

    reader.seekToFirstEvent();

    final Iterable<LoggedEvent> iterable = () -> reader;

    return StreamSupport.stream(iterable.spliterator(), false);
  }

  public FluentLogWriter newRecord(final String logName) {
    final LogStream logStream = getLogStream(logName);
    return new FluentLogWriter(logStream);
  }

  protected StateStorageFactory getStateStorageFactory() {
    if (stateStorageFactory == null) {
      final File rocksDBDirectory = new File(storageDirectory.getRoot(), "state");
      if (!rocksDBDirectory.exists()) {
        rocksDBDirectory.mkdir();
      }

      stateStorageFactory = new StateStorageFactory(rocksDBDirectory);
    }

    return stateStorageFactory;
  }

  public StreamProcessorControl initStreamProcessor(
      final String log,
      final int streamProcessorId,
      final ZeebeDbFactory zeebeDbFactory,
      final StreamProcessorFactory streamProcessorFactory) {
    final LogStream stream = getLogStream(log);

    final StreamProcessorControlImpl control =
        new StreamProcessorControlImpl(
            stream, zeebeDbFactory, streamProcessorFactory, streamProcessorId);

    closeables.manage(control);

    return control;
  }

  protected class StreamProcessorControlImpl implements StreamProcessorControl, AutoCloseable {

    private final StreamProcessorFactory factory;
    private final int streamProcessorId;
    private final LogStream stream;
    private final ZeebeDbFactory zeebeDbFactory;

    protected final SuspendableStreamProcessor currentStreamProcessor;
    protected StreamProcessorController currentController;
    protected StreamProcessorService currentStreamProcessorService;
    protected SnapshotController currentSnapshotController;

    public StreamProcessorControlImpl(
        final LogStream stream,
        final ZeebeDbFactory zeebeDbFactory,
        final StreamProcessorFactory streamProcessorFactory,
        final int streamProcessorId) {
      this.stream = stream;
      this.zeebeDbFactory = zeebeDbFactory;
      this.currentStreamProcessor = new SuspendableStreamProcessor();
      this.factory = streamProcessorFactory;
      this.streamProcessorId = streamProcessorId;
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
    public void blockAfterEvent(final Predicate<LoggedEvent> test) {
      currentStreamProcessor.blockAfterEvent(test);
    }

    @Override
    public void blockAfterJobEvent(final Predicate<TypedRecord<JobRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isJobRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, JobRecord.class)));
    }

    @Override
    public void blockAfterDeploymentEvent(final Predicate<TypedRecord<DeploymentRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isDeploymentRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, DeploymentRecord.class)));
    }

    @Override
    public void blockAfterWorkflowInstanceRecord(
        final Predicate<TypedRecord<WorkflowInstanceRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isWorkflowInstanceRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, WorkflowInstanceRecord.class)));
    }

    @Override
    public void blockAfterWorkflowInstanceCreationRecord(
        final Predicate<TypedRecord<WorkflowInstanceCreationRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isWorkflowInstanceCreationRecord(e)
                  && test.test(
                      CopiedTypedEvent.toTypedEvent(e, WorkflowInstanceCreationRecord.class)));
    }

    @Override
    public void blockAfterIncidentEvent(final Predicate<TypedRecord<IncidentRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isIncidentRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, IncidentRecord.class)));
    }

    @Override
    public void blockAfterMessageEvent(final Predicate<TypedRecord<MessageRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isMessageRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, MessageRecord.class)));
    }

    @Override
    public void blockAfterMessageSubscriptionEvent(
        final Predicate<TypedRecord<MessageSubscriptionRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isMessageSubscriptionRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, MessageSubscriptionRecord.class)));
    }

    @Override
    public void blockAfterWorkflowInstanceSubscriptionEvent(
        final Predicate<TypedRecord<WorkflowInstanceSubscriptionRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isWorkflowInstanceSubscriptionRecord(e)
                  && test.test(
                      CopiedTypedEvent.toTypedEvent(e, WorkflowInstanceSubscriptionRecord.class)));
    }

    @Override
    public void blockAfterTimerEvent(final Predicate<TypedRecord<TimerRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isTimerRecord(e)
                  && test.test(CopiedTypedEvent.toTypedEvent(e, TimerRecord.class)));
    }

    @Override
    public void blockAfterMessageStartEventSubscriptionRecord(
        final Predicate<TypedRecord<MessageStartEventSubscriptionRecord>> test) {
      blockAfterEvent(
          e ->
              Records.isMessageStartEventSubscriptionRecord(e)
                  && test.test(
                      CopiedTypedEvent.toTypedEvent(e, MessageStartEventSubscriptionRecord.class)));
    }

    @Override
    public void close() {
      if (currentController != null && currentController.isOpened()) {
        currentStreamProcessorService.close();
      }

      currentStreamProcessorService = null;
      currentController = null;
      currentStreamProcessor.wrap(null, null);
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

    @Override
    public SnapshotController getSnapshotController() {
      return currentSnapshotController;
    }

    private StreamProcessorService buildStreamProcessorController() {
      final String name = "processor";

      final StateStorage stateStorage = getStateStorageFactory().create(streamProcessorId, name);
      currentSnapshotController = spy(new StateSnapshotController(zeebeDbFactory, stateStorage));

      final ActorFuture<Void> openFuture = new CompletableActorFuture<>();

      final StreamProcessorService processorService =
          StreamProcessors.createStreamProcessor(name, streamProcessorId)
              .logStream(stream)
              .snapshotController(currentSnapshotController)
              .actorScheduler(actorScheduler)
              .serviceContainer(serviceContainer)
              .streamProcessorFactory(
                  (actor, zeebeDb, dbContext) -> {
                    currentStreamProcessor.wrap(
                        factory.createProcessor(actor, zeebeDb, dbContext), openFuture);
                    return currentStreamProcessor;
                  })
              .build()
              .join();
      openFuture.join();
      return processorService;
    }
  }

  public static class SuspendableStreamProcessor implements StreamProcessor {
    protected StreamProcessor wrappedProcessor;

    protected AtomicReference<Predicate<LoggedEvent>> blockAfterCondition =
        new AtomicReference<>(null);

    private final RecordMetadata metadata = new RecordMetadata();
    private final ErrorRecord errorRecord = new ErrorRecord();
    protected boolean blockAfterCurrentEvent;
    private StreamProcessorContext context;
    private ActorFuture<Void> openFuture;

    public void wrap(StreamProcessor streamProcessor, ActorFuture<Void> openFuture) {
      wrappedProcessor = streamProcessor;
      this.openFuture = openFuture;
    }

    public void resume() {
      context
          .getActorControl()
          .call(
              () -> {
                context.resumeController();
              });
    }

    public void blockAfterEvent(final Predicate<LoggedEvent> test) {
      this.blockAfterCondition.set(test);
    }

    @Override
    public EventProcessor onEvent(final LoggedEvent event) {
      final Predicate<LoggedEvent> suspensionCondition = this.blockAfterCondition.get();
      blockAfterCurrentEvent = suspensionCondition != null && suspensionCondition.test(event);

      final EventProcessor actualProcessor = wrappedProcessor.onEvent(event);

      return new EventProcessor() {

        @Override
        public void processEvent() {
          if (actualProcessor != null) {
            actualProcessor.processEvent();
          }
        }

        @Override
        public void onError(Throwable throwable) {
          if (actualProcessor != null) {
            actualProcessor.onError(throwable);
          }
        }

        @Override
        public boolean executeSideEffects() {
          return actualProcessor == null || actualProcessor.executeSideEffects();
        }

        @Override
        public long writeEvent(final LogStreamRecordWriter writer) {
          final long result = actualProcessor != null ? actualProcessor.writeEvent(writer) : 0;

          if (blockAfterCurrentEvent) {
            blockAfterCurrentEvent = false;
            context.suspendController();
          }
          return result;
        }
      };
    }

    @Override
    public long getPositionToRecoverFrom() {
      return wrappedProcessor.getPositionToRecoverFrom();
    }

    @Override
    public void onOpen(final StreamProcessorContext context) {
      this.context = context;
      wrappedProcessor.onOpen(this.context);
      openFuture.complete(null);
    }

    @Override
    public void onRecovered() {
      wrappedProcessor.onRecovered();
    }

    @Override
    public void onClose() {
      wrappedProcessor.onClose();
    }

    @Override
    public long getFailedPosition(LoggedEvent currentEvent) {
      metadata.reset();
      currentEvent.readMetadata(metadata);

      if (metadata.getValueType() == ValueType.ERROR) {
        currentEvent.readValue(errorRecord);
        return errorRecord.getErrorEventPosition();
      }
      return -1;
    }
  }

  public static class FluentLogWriter {

    protected RecordMetadata metadata = new RecordMetadata();
    protected UnpackedObject value;
    protected LogStream logStream;
    protected long key = -1;
    private long sourceRecordPosition = -1;
    private int producerId = -1;

    public FluentLogWriter(final LogStream logStream) {
      this.logStream = logStream;

      metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
    }

    public FluentLogWriter intent(final Intent intent) {
      this.metadata.intent(intent);
      return this;
    }

    public FluentLogWriter requestId(final long requestId) {
      this.metadata.requestId(requestId);
      return this;
    }

    public FluentLogWriter producerId(final int producerId) {
      this.producerId = producerId;
      return this;
    }

    public FluentLogWriter sourceRecordPosition(final long sourceRecordPosition) {
      this.sourceRecordPosition = sourceRecordPosition;
      return this;
    }

    public FluentLogWriter requestStreamId(final int requestStreamId) {
      this.metadata.requestStreamId(requestStreamId);
      return this;
    }

    public FluentLogWriter recordType(final RecordType recordType) {
      this.metadata.recordType(recordType);
      return this;
    }

    public FluentLogWriter key(final long key) {
      this.key = key;
      return this;
    }

    public FluentLogWriter event(final UnpackedObject event) {
      final ValueType eventType = VALUE_TYPES.get(event.getClass());
      if (eventType == null) {
        throw new RuntimeException("No event type registered for getValue " + event.getClass());
      }

      this.metadata.valueType(eventType);
      this.value = event;
      return this;
    }

    public long write() {
      final LogStreamRecordWriter writer = new LogStreamWriterImpl(logStream);

      writer.sourceRecordPosition(sourceRecordPosition);
      writer.producerId(producerId);

      if (key >= 0) {
        writer.key(key);
      } else {
        writer.keyNull();
      }

      writer.metadataWriter(metadata);
      writer.valueWriter(value);

      return doRepeatedly(() -> writer.tryWrite()).until(p -> p >= 0);
    }
  }
}
