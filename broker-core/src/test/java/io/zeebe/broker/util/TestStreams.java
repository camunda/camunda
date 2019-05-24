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

import static io.zeebe.engine.processor.StreamProcessorServiceNames.streamProcessorService;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.distributedlog.impl.DistributedLogstreamServiceConfig;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedEventRegistry;
import io.zeebe.engine.processor.TypedRecordProcessorFactory;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.rules.TemporaryFolder;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.stubbing.Answer;

public class TestStreams {

  protected static final Map<Class<?>, ValueType> VALUE_TYPES = new HashMap<>();

  static {
    TypedEventRegistry.EVENT_REGISTRY.forEach((v, c) -> VALUE_TYPES.put(c, v));

    VALUE_TYPES.put(UnpackedObject.class, ValueType.NOOP);
  }

  protected final TemporaryFolder storageDirectory;
  protected final AutoCloseableRule closeables;
  private final ServiceContainer serviceContainer;

  private final Map<String, LogStream> managedLogs = new HashMap<>();
  private final Map<String, StateSnapshotController> snapshotControllerMap = new HashMap<>();

  protected ActorScheduler actorScheduler;

  protected StateStorageFactory stateStorageFactory;
  public static final String PROCESSOR_NAME = "processor";
  private final CommandResponseWriter mockCommandResponseWriter;

  public TestStreams(
      final TemporaryFolder storageDirectory,
      final AutoCloseableRule closeables,
      final ServiceContainer serviceContainer,
      final ActorScheduler actorScheduler) {
    this.storageDirectory = storageDirectory;
    this.closeables = closeables;
    this.serviceContainer = serviceContainer;
    this.actorScheduler = actorScheduler;

    mockCommandResponseWriter = mock(CommandResponseWriter.class);
    when(mockCommandResponseWriter.intent(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.key(anyLong())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.partitionId(anyInt())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.recordType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionReason(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueWriter(any())).thenReturn(mockCommandResponseWriter);

    when(mockCommandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);
  }

  public CommandResponseWriter getMockedResponseWriter() {
    return mockCommandResponseWriter;
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
        spy(
            LogStreams.createFsLogStream(partitionId)
                .logRootPath(segments.getAbsolutePath())
                .serviceContainer(serviceContainer)
                .logName(name)
                .deleteOnClose(true)
                .indexStateStorage(stateStorage)
                .build()
                .join());

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

  public StateStorageFactory getStateStorageFactory() {
    if (stateStorageFactory == null) {
      final File rocksDBDirectory = new File(storageDirectory.getRoot(), "state");
      if (!rocksDBDirectory.exists()) {
        rocksDBDirectory.mkdir();
      }

      stateStorageFactory = new StateStorageFactory(rocksDBDirectory);
    }

    return stateStorageFactory;
  }

  public StreamProcessor startStreamProcessor(
      final String log,
      final int streamProcessorId,
      final ZeebeDbFactory zeebeDbFactory,
      final TypedRecordProcessorFactory typedRecordProcessorFactory) {
    final LogStream stream = getLogStream(log);
    return buildStreamProcessorController(
        streamProcessorId, stream, zeebeDbFactory, typedRecordProcessorFactory);
  }

  private StreamProcessor buildStreamProcessorController(
      int streamProcessorId,
      LogStream stream,
      ZeebeDbFactory zeebeDbFactory,
      TypedRecordProcessorFactory factory) {

    final StateStorage stateStorage =
        getStateStorageFactory().create(streamProcessorId, PROCESSOR_NAME);
    final StateSnapshotController currentSnapshotController =
        spy(new StateSnapshotController(zeebeDbFactory, stateStorage));
    snapshotControllerMap.put(stream.getLogName(), currentSnapshotController);

    final ActorFuture<Void> openFuture = new CompletableActorFuture<>();

    final StreamProcessor processorService =
        StreamProcessor.builder(streamProcessorId, PROCESSOR_NAME)
            .logStream(stream)
            .snapshotController(currentSnapshotController)
            .actorScheduler(actorScheduler)
            .serviceContainer(serviceContainer)
            .commandResponseWriter(mockCommandResponseWriter)
            .streamProcessorFactory(
                (context) -> {
                  final TypedRecordProcessors processors = factory.createProcessors(context);
                  processors.withListener(
                      new StreamProcessorLifecycleAware() {
                        @Override
                        public void onOpen(ReadonlyProcessingContext context) {
                          openFuture.complete(null);
                        }
                      });
                  return processors;
                })
            .build()
            .join();
    openFuture.join();
    return processorService;
  }

  public StateSnapshotController getStateSnapshotController(String stream) {
    return snapshotControllerMap.get(stream);
  }

  public void closeProcessor(String streamName) {
    serviceContainer.removeService(streamProcessorService(streamName, PROCESSOR_NAME)).join();
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
