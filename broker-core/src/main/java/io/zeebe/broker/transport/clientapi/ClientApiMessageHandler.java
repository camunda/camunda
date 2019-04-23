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
package io.zeebe.broker.transport.clientapi;

import io.zeebe.broker.Loggers;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerRequestHandler;
import java.util.EnumMap;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;

public class ClientApiMessageHandler implements ServerMessageHandler, ServerRequestHandler {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  protected final ExecuteCommandRequestDecoder executeCommandRequestDecoder =
      new ExecuteCommandRequestDecoder();
  protected final ManyToOneConcurrentLinkedQueue<Runnable> cmdQueue =
      new ManyToOneConcurrentLinkedQueue<>();
  protected final Consumer<Runnable> cmdConsumer = Runnable::run;

  protected final Int2ObjectHashMap<Partition> leaderPartitions = new Int2ObjectHashMap<>();
  protected final RecordMetadata eventMetadata = new RecordMetadata();
  protected final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl();

  protected final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter();

  protected final EnumMap<ValueType, UnpackedObject> recordsByType = new EnumMap<>(ValueType.class);

  public ClientApiMessageHandler() {
    initEventTypeMap();
  }

  private void initEventTypeMap() {
    recordsByType.put(ValueType.DEPLOYMENT, new DeploymentRecord());
    recordsByType.put(ValueType.JOB, new JobRecord());
    recordsByType.put(ValueType.WORKFLOW_INSTANCE, new WorkflowInstanceRecord());
    recordsByType.put(ValueType.MESSAGE, new MessageRecord());
    recordsByType.put(ValueType.JOB_BATCH, new JobBatchRecord());
    recordsByType.put(ValueType.INCIDENT, new IncidentRecord());
    recordsByType.put(ValueType.VARIABLE_DOCUMENT, new VariableDocumentRecord());
    recordsByType.put(ValueType.WORKFLOW_INSTANCE_CREATION, new WorkflowInstanceCreationRecord());
  }

  private boolean handleExecuteCommandRequest(
      final ServerOutput output,
      final RemoteAddress requestAddress,
      final long requestId,
      final RecordMetadata eventMetadata,
      final DirectBuffer buffer,
      final int messageOffset,
      final int messageLength) {
    executeCommandRequestDecoder.wrap(
        buffer,
        messageOffset + messageHeaderDecoder.encodedLength(),
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    final int partitionId = executeCommandRequestDecoder.partitionId();
    final long key = executeCommandRequestDecoder.key();

    final Partition partition = leaderPartitions.get(partitionId);

    if (partition == null) {
      return errorResponseWriter
          .partitionLeaderMismatch(partitionId)
          .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
    }

    final ValueType eventType = executeCommandRequestDecoder.valueType();
    final short intent = executeCommandRequestDecoder.intent();
    final UnpackedObject event = recordsByType.get(eventType);

    if (event == null) {
      return errorResponseWriter
          .unsupportedMessage(eventType.name(), recordsByType.keySet().toArray())
          .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
    }

    final int eventOffset =
        executeCommandRequestDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();
    final int eventLength = executeCommandRequestDecoder.valueLength();

    event.reset();

    try {
      // verify that the event / command is valid
      event.wrap(buffer, eventOffset, eventLength);
    } catch (RuntimeException e) {
      LOG.error("Failed to deserialize message of type {} in client API", eventType.name(), e);

      return errorResponseWriter
          .malformedRequest(e)
          .tryWriteResponseOrLogFailure(output, requestAddress.getStreamId(), requestId);
    }

    eventMetadata.recordType(RecordType.COMMAND);
    eventMetadata.intent(Intent.fromProtocolValue(eventType, intent));
    eventMetadata.valueType(eventType);

    logStreamWriter.wrap(partition.getLogStream());

    if (key != ExecuteCommandRequestDecoder.keyNullValue()) {
      logStreamWriter.key(key);
    } else {
      logStreamWriter.keyNull();
    }

    final long eventPosition =
        logStreamWriter
            .metadataWriter(eventMetadata)
            .value(buffer, eventOffset, eventLength)
            .tryWrite();

    return eventPosition >= 0;
  }

  public void addPartition(final Partition partition) {
    cmdQueue.add(() -> leaderPartitions.put(partition.getPartitionId(), partition));
  }

  public void removePartition(final Partition partition) {
    cmdQueue.add(() -> leaderPartitions.remove(partition.getPartitionId()));
  }

  @Override
  public boolean onRequest(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length,
      final long requestId) {
    drainCommandQueue();

    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    final int clientVersion = messageHeaderDecoder.version();

    if (clientVersion > Protocol.PROTOCOL_VERSION) {
      return errorResponseWriter
          .invalidClientVersion(Protocol.PROTOCOL_VERSION, clientVersion)
          .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
    }

    eventMetadata.reset();
    eventMetadata.protocolVersion(clientVersion);
    eventMetadata.requestId(requestId);
    eventMetadata.requestStreamId(remoteAddress.getStreamId());

    if (templateId == ExecuteCommandRequestDecoder.TEMPLATE_ID) {
      return handleExecuteCommandRequest(
          output, remoteAddress, requestId, eventMetadata, buffer, offset, length);
    }

    return errorResponseWriter
        .invalidMessageTemplate(templateId, ExecuteCommandRequestDecoder.TEMPLATE_ID)
        .tryWriteResponse(output, remoteAddress.getStreamId(), requestId);
  }

  @Override
  public boolean onMessage(
      final ServerOutput output,
      final RemoteAddress remoteAddress,
      final DirectBuffer buffer,
      final int offset,
      final int length) {
    // ignore; currently no incoming single-message client interactions
    return true;
  }

  private void drainCommandQueue() {
    while (!cmdQueue.isEmpty()) {
      final Runnable runnable = cmdQueue.poll();
      if (runnable != null) {
        cmdConsumer.accept(runnable);
      }
    }
  }
}
