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
import io.zeebe.broker.subscription.MessageHeaderDecoder;
import io.zeebe.broker.subscription.OpenMessageSubscriptionDecoder;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.ServerMessageHandler;
import io.zeebe.transport.ServerOutput;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;

public class SubscriptionApiCommandMessageHandler implements ServerMessageHandler {

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();

  private final OpenMessageSubscriptionCommand openMessageSubscriptionCommand =
      new OpenMessageSubscriptionCommand();

  private final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl();
  private final RecordMetadata recordMetadata = new RecordMetadata();

  private final MessageSubscriptionRecord messageSubscriptionRecord =
      new MessageSubscriptionRecord();

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

        default:
          break;
      }
    }

    return true;
  }

  private boolean onOpenMessageSubscription(DirectBuffer buffer, int offset, int length) {
    openMessageSubscriptionCommand.wrap(buffer, offset, length);

    final int subscriptionPartitionId = openMessageSubscriptionCommand.getSubscriptionPartitionId();
    final Partition partition = leaderPartitions.get(subscriptionPartitionId);
    if (partition == null) {
      // ignore message if you are not the leader of the partition
      return true;
    }

    logStreamWriter.wrap(partition.getLogStream());

    return writeOpenMessageSubscriptionCommand();
  }

  private boolean writeOpenMessageSubscriptionCommand() {

    recordMetadata
        .reset()
        .recordType(RecordType.COMMAND)
        .valueType(ValueType.MESSAGE_SUBSCRIPTION)
        .intent(MessageSubscriptionIntent.OPEN);

    messageSubscriptionRecord
        .setWorkflowInstancePartitionId(
            openMessageSubscriptionCommand.getWorkflowInstancePartitionId())
        .setWorkflowInstanceKey(openMessageSubscriptionCommand.getWorkflowInstanceKey())
        .setActivityInstanceKey(openMessageSubscriptionCommand.getActivityInstanceKey())
        .setMessageName(openMessageSubscriptionCommand.getMessageName())
        .setCorrelationKey(openMessageSubscriptionCommand.getCorrelationKey());

    final long position =
        logStreamWriter
            .positionAsKey()
            .metadataWriter(recordMetadata)
            .valueWriter(messageSubscriptionRecord)
            .tryWrite();

    return position > 0;
  }
}
