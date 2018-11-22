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
package io.zeebe.broker.subscription.command;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.subscription.CloseMessageSubscriptionDecoder;
import io.zeebe.broker.subscription.CloseWorkflowInstanceSubscriptionDecoder;
import io.zeebe.broker.subscription.CorrelateMessageSubscriptionDecoder;
import io.zeebe.broker.subscription.CorrelateWorkflowInstanceSubscriptionDecoder;
import io.zeebe.broker.subscription.MessageHeaderDecoder;
import io.zeebe.broker.subscription.OpenMessageSubscriptionDecoder;
import io.zeebe.broker.subscription.OpenWorkflowInstanceSubscriptionDecoder;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

public class SubscriptionApiCommandMessageHandler implements ServerMessageHandler {

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final OpenMessageSubscriptionCommand openMessageSubscriptionCommand =
      new OpenMessageSubscriptionCommand();

  private final OpenWorkflowInstanceSubscriptionCommand openWorkflowInstanceSubscriptionCommand =
      new OpenWorkflowInstanceSubscriptionCommand();

  private final CorrelateWorkflowInstanceSubscriptionCommand
      correlateWorkflowInstanceSubscriptionCommand =
          new CorrelateWorkflowInstanceSubscriptionCommand();

  private final CorrelateMessageSubscriptionCommand correlateMessageSubscriptionCommand =
      new CorrelateMessageSubscriptionCommand();

  private final CloseMessageSubscriptionCommand closeMessageSubscriptionCommand =
      new CloseMessageSubscriptionCommand();

  private final CloseWorkflowInstanceSubscriptionCommand closeWorkflowInstanceSubscriptionCommand =
      new CloseWorkflowInstanceSubscriptionCommand();

  private final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl();
  private final RecordMetadata recordMetadata = new RecordMetadata();

  private final MessageSubscriptionRecord messageSubscriptionRecord =
      new MessageSubscriptionRecord();

  private final WorkflowInstanceSubscriptionRecord workflowInstanceSubscriptionRecord =
      new WorkflowInstanceSubscriptionRecord();

  private final Int2ObjectHashMap<Partition> leaderPartitions;

  public SubscriptionApiCommandMessageHandler(Int2ObjectHashMap<Partition> leaderPartitions) {
    this.leaderPartitions = leaderPartitions;
  }

  @Override
  public boolean onMessage(
      ServerOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length) {

    messageHeaderDecoder.wrap(buffer, offset);

    if (messageHeaderDecoder.schemaId() == OpenMessageSubscriptionDecoder.SCHEMA_ID) {

      switch (messageHeaderDecoder.templateId()) {
        case OpenMessageSubscriptionDecoder.TEMPLATE_ID:
          return onOpenMessageSubscription(buffer, offset, length);

        case OpenWorkflowInstanceSubscriptionDecoder.TEMPLATE_ID:
          return onOpenWorkflowInstanceSubscription(buffer, offset, length);

        case CorrelateWorkflowInstanceSubscriptionDecoder.TEMPLATE_ID:
          return onCorrelateWorkflowInstanceSubscription(buffer, offset, length);

        case CorrelateMessageSubscriptionDecoder.TEMPLATE_ID:
          return onCorrelateMessageSubscription(buffer, offset, length);

        case CloseMessageSubscriptionDecoder.TEMPLATE_ID:
          return onCloseMessageSubscription(buffer, offset, length);

        case CloseWorkflowInstanceSubscriptionDecoder.TEMPLATE_ID:
          return onCloseWorkflowInstanceSubscription(buffer, offset, length);

        default:
          break;
      }
    }

    return true;
  }

  private boolean onOpenMessageSubscription(DirectBuffer buffer, int offset, int length) {
    openMessageSubscriptionCommand.wrap(buffer, offset, length);

    messageSubscriptionRecord
        .setWorkflowInstanceKey(openMessageSubscriptionCommand.getWorkflowInstanceKey())
        .setElementInstanceKey(openMessageSubscriptionCommand.getElementInstanceKey())
        .setMessageName(openMessageSubscriptionCommand.getMessageName())
        .setCorrelationKey(openMessageSubscriptionCommand.getCorrelationKey());

    return writeCommand(
        openMessageSubscriptionCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.OPEN,
        messageSubscriptionRecord);
  }

  private boolean onOpenWorkflowInstanceSubscription(DirectBuffer buffer, int offset, int length) {
    openWorkflowInstanceSubscriptionCommand.wrap(buffer, offset, length);

    final long workflowInstanceKey =
        openWorkflowInstanceSubscriptionCommand.getWorkflowInstanceKey();
    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    workflowInstanceSubscriptionRecord.reset();
    workflowInstanceSubscriptionRecord
        .setSubscriptionPartitionId(
            openWorkflowInstanceSubscriptionCommand.getSubscriptionPartitionId())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setElementInstanceKey(openWorkflowInstanceSubscriptionCommand.getElementInstanceKey())
        .setMessageName(openWorkflowInstanceSubscriptionCommand.getMessageName());

    return writeCommand(
        workflowInstancePartitionId,
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
        WorkflowInstanceSubscriptionIntent.OPEN,
        workflowInstanceSubscriptionRecord);
  }

  private boolean onCorrelateWorkflowInstanceSubscription(
      DirectBuffer buffer, int offset, int length) {
    correlateWorkflowInstanceSubscriptionCommand.wrap(buffer, offset, length);

    final long workflowInstanceKey =
        correlateWorkflowInstanceSubscriptionCommand.getWorkflowInstanceKey();
    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    workflowInstanceSubscriptionRecord
        .setSubscriptionPartitionId(
            correlateWorkflowInstanceSubscriptionCommand.getSubscriptionPartitionId())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setElementInstanceKey(correlateWorkflowInstanceSubscriptionCommand.getElementInstanceKey())
        .setMessageName(correlateWorkflowInstanceSubscriptionCommand.getMessageName())
        .setPayload(correlateWorkflowInstanceSubscriptionCommand.getPayload());

    return writeCommand(
        workflowInstancePartitionId,
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
        WorkflowInstanceSubscriptionIntent.CORRELATE,
        workflowInstanceSubscriptionRecord);
  }

  private boolean onCorrelateMessageSubscription(DirectBuffer buffer, int offset, int length) {
    correlateMessageSubscriptionCommand.wrap(buffer, offset, length);

    messageSubscriptionRecord.reset();
    messageSubscriptionRecord
        .setWorkflowInstanceKey(correlateMessageSubscriptionCommand.getWorkflowInstanceKey())
        .setElementInstanceKey(correlateMessageSubscriptionCommand.getElementInstanceKey())
        .setMessageName(correlateMessageSubscriptionCommand.getMessageName());

    return writeCommand(
        correlateMessageSubscriptionCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CORRELATE,
        messageSubscriptionRecord);
  }

  private boolean onCloseMessageSubscription(DirectBuffer buffer, int offset, int length) {
    closeMessageSubscriptionCommand.wrap(buffer, offset, length);

    messageSubscriptionRecord.reset();
    messageSubscriptionRecord
        .setWorkflowInstanceKey(closeMessageSubscriptionCommand.getWorkflowInstanceKey())
        .setElementInstanceKey(closeMessageSubscriptionCommand.getElementInstanceKey())
        .setMessageName(closeMessageSubscriptionCommand.getMessageName());

    return writeCommand(
        closeMessageSubscriptionCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.CLOSE,
        messageSubscriptionRecord);
  }

  private boolean onCloseWorkflowInstanceSubscription(DirectBuffer buffer, int offset, int length) {
    closeWorkflowInstanceSubscriptionCommand.wrap(buffer, offset, length);

    final long workflowInstanceKey =
        closeWorkflowInstanceSubscriptionCommand.getWorkflowInstanceKey();
    final int workflowInstancePartitionId = Protocol.decodePartitionId(workflowInstanceKey);

    workflowInstanceSubscriptionRecord.reset();
    workflowInstanceSubscriptionRecord
        .setSubscriptionPartitionId(
            closeWorkflowInstanceSubscriptionCommand.getSubscriptionPartitionId())
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setElementInstanceKey(closeWorkflowInstanceSubscriptionCommand.getElementInstanceKey())
        .setMessageName(closeWorkflowInstanceSubscriptionCommand.getMessageName());

    return writeCommand(
        workflowInstancePartitionId,
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
        WorkflowInstanceSubscriptionIntent.CLOSE,
        workflowInstanceSubscriptionRecord);
  }

  private boolean writeCommand(
      int partitionId, ValueType valueType, Intent intent, UnpackedObject command) {

    final Partition partition = leaderPartitions.get(partitionId);
    if (partition == null) {
      // ignore message if you are not the leader of the partition
      return true;
    }

    logStreamWriter.wrap(partition.getLogStream());

    recordMetadata.reset().recordType(RecordType.COMMAND).valueType(valueType).intent(intent);

    final long position =
        logStreamWriter
            .positionAsKey()
            .metadataWriter(recordMetadata)
            .valueWriter(command)
            .tryWrite();

    return position > 0;
  }
}
