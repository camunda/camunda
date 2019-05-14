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

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.distributedlog.impl.DistributedLogstreamServiceConfig;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.log.LoggedEvent;
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

  public static class FluentLogWriter {

    protected RecordMetadata metadata = new RecordMetadata();
    protected UnpackedObject value;
    protected LogStream logStream;
    protected long key = -1;

    public FluentLogWriter(final LogStream logStream) {
      this.logStream = logStream;

      metadata.protocolVersion(Protocol.PROTOCOL_VERSION);
    }

    public FluentLogWriter intent(final Intent intent) {
      this.metadata.intent(intent);
      return this;
    }

    public FluentLogWriter recordType(final RecordType recordType) {
      this.metadata.recordType(recordType);
      return this;
    }

    public TestStreams.FluentLogWriter key(final long key) {
      this.key = key;
      return this;
    }

    public TestStreams.FluentLogWriter event(final UnpackedObject event) {
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

      writer.sourceRecordPosition(-1);
      writer.producerId(-1);

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
