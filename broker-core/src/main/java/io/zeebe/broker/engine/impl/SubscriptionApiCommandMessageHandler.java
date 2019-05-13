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
package io.zeebe.broker.engine.impl;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.engine.processor.workflow.message.command.CloseMessageSubscriptionCommand;
import io.zeebe.engine.processor.workflow.message.command.CloseMessageSubscriptionDecoder;
import io.zeebe.engine.processor.workflow.message.command.CloseWorkflowInstanceSubscriptionCommand;
import io.zeebe.engine.processor.workflow.message.command.CloseWorkflowInstanceSubscriptionDecoder;
import io.zeebe.engine.processor.workflow.message.command.CorrelateMessageSubscriptionCommand;
import io.zeebe.engine.processor.workflow.message.command.CorrelateMessageSubscriptionDecoder;
import io.zeebe.engine.processor.workflow.message.command.CorrelateWorkflowInstanceSubscriptionCommand;
import io.zeebe.engine.processor.workflow.message.command.CorrelateWorkflowInstanceSubscriptionDecoder;
import io.zeebe.engine.processor.workflow.message.command.MessageHeaderDecoder;
import io.zeebe.engine.processor.workflow.message.command.OpenMessageSubscriptionCommand;
import io.zeebe.engine.processor.workflow.message.command.OpenMessageSubscriptionDecoder;
import io.zeebe.engine.processor.workflow.message.command.OpenWorkflowInstanceSubscriptionCommand;
import io.zeebe.engine.processor.workflow.message.command.OpenWorkflowInstanceSubscriptionDecoder;
import io.zeebe.engine.processor.workflow.message.command.RejectCorrelateMessageSubscriptionCommand;
import io.zeebe.engine.processor.workflow.message.command.RejectCorrelateMessageSubscriptionDecoder;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.sched.ActorControl;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;

public class SubscriptionApiCommandMessageHandler
    implements Function<byte[], CompletableFuture<Void>> {

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

  private final RejectCorrelateMessageSubscriptionCommand resetMessageCorrelationCommand =
      new RejectCorrelateMessageSubscriptionCommand();

  private final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl();
  private final RecordMetadata recordMetadata = new RecordMetadata();

  private final MessageSubscriptionRecord messageSubscriptionRecord =
      new MessageSubscriptionRecord();

  private final WorkflowInstanceSubscriptionRecord workflowInstanceSubscriptionRecord =
      new WorkflowInstanceSubscriptionRecord();

  private final Int2ObjectHashMap<Partition> leaderPartitions;
  private final ActorControl actor;

  public SubscriptionApiCommandMessageHandler(
      ActorControl actor, Int2ObjectHashMap<Partition> leaderPartitions) {
    this.leaderPartitions = leaderPartitions;
    this.actor = actor;
  }

  @Override
  public CompletableFuture<Void> apply(byte[] bytes) {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    actor.call(
        () -> {
          final DirectBuffer buffer = new UnsafeBuffer(bytes);
          final int offset = 0;
          final int length = buffer.capacity();
          messageHeaderDecoder.wrap(buffer, offset);

          if (messageHeaderDecoder.schemaId() == OpenMessageSubscriptionDecoder.SCHEMA_ID) {

            switch (messageHeaderDecoder.templateId()) {
              case OpenMessageSubscriptionDecoder.TEMPLATE_ID:
                onOpenMessageSubscription(buffer, offset, length);
                break;
              case OpenWorkflowInstanceSubscriptionDecoder.TEMPLATE_ID:
                onOpenWorkflowInstanceSubscription(buffer, offset, length);
                break;
              case CorrelateWorkflowInstanceSubscriptionDecoder.TEMPLATE_ID:
                onCorrelateWorkflowInstanceSubscription(buffer, offset, length);
                break;
              case CorrelateMessageSubscriptionDecoder.TEMPLATE_ID:
                onCorrelateMessageSubscription(buffer, offset, length);
                break;
              case CloseMessageSubscriptionDecoder.TEMPLATE_ID:
                onCloseMessageSubscription(buffer, offset, length);
                break;
              case CloseWorkflowInstanceSubscriptionDecoder.TEMPLATE_ID:
                onCloseWorkflowInstanceSubscription(buffer, offset, length);
                break;
              case RejectCorrelateMessageSubscriptionDecoder.TEMPLATE_ID:
                onRejectCorrelateMessageSubscription(buffer, offset, length);
                break;
              default:
                break;
            }
          }
          future.complete(null);
        });
    return future;
  }

  private boolean onOpenMessageSubscription(DirectBuffer buffer, int offset, int length) {
    openMessageSubscriptionCommand.wrap(buffer, offset, length);

    messageSubscriptionRecord
        .setWorkflowInstanceKey(openMessageSubscriptionCommand.getWorkflowInstanceKey())
        .setElementInstanceKey(openMessageSubscriptionCommand.getElementInstanceKey())
        .setMessageKey(-1)
        .setMessageName(openMessageSubscriptionCommand.getMessageName())
        .setCorrelationKey(openMessageSubscriptionCommand.getCorrelationKey())
        .setCloseOnCorrelate(openMessageSubscriptionCommand.shouldCloseOnCorrelate());

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
        .setMessageKey(-1)
        .setMessageName(openWorkflowInstanceSubscriptionCommand.getMessageName())
        .setCloseOnCorrelate(openWorkflowInstanceSubscriptionCommand.shouldCloseOnCorrelate());

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
        .setMessageKey(correlateWorkflowInstanceSubscriptionCommand.getMessageKey())
        .setMessageName(correlateWorkflowInstanceSubscriptionCommand.getMessageName())
        .setVariables(correlateWorkflowInstanceSubscriptionCommand.getVariables());

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
        .setMessageKey(-1)
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
        .setMessageKey(-1L)
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
        .setMessageKey(-1)
        .setMessageName(closeWorkflowInstanceSubscriptionCommand.getMessageName());

    return writeCommand(
        workflowInstancePartitionId,
        ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
        WorkflowInstanceSubscriptionIntent.CLOSE,
        workflowInstanceSubscriptionRecord);
  }

  private boolean onRejectCorrelateMessageSubscription(
      DirectBuffer buffer, int offset, int length) {
    resetMessageCorrelationCommand.wrap(buffer, offset, length);

    final long workflowInstanceKey = resetMessageCorrelationCommand.getWorkflowInstanceKey();

    messageSubscriptionRecord.reset();
    messageSubscriptionRecord
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setElementInstanceKey(-1L)
        .setMessageName(resetMessageCorrelationCommand.getMessageName())
        .setCorrelationKey(resetMessageCorrelationCommand.getCorrelationKey())
        .setMessageKey(resetMessageCorrelationCommand.getMessageKey())
        .setCloseOnCorrelate(false);

    return writeCommand(
        resetMessageCorrelationCommand.getSubscriptionPartitionId(),
        ValueType.MESSAGE_SUBSCRIPTION,
        MessageSubscriptionIntent.REJECT,
        messageSubscriptionRecord);
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
        logStreamWriter.key(-1).metadataWriter(recordMetadata).valueWriter(command).tryWrite();

    return position > 0;
  }
}
