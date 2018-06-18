/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl.subscription;

import static io.zeebe.util.VarDataUtil.readBytes;

import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.record.UntypedRecordImpl;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.SubscribedRecordDecoder;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.transport.ClientMessageHandler;
import io.zeebe.transport.ClientOutput;
import io.zeebe.transport.RemoteAddress;
import java.nio.charset.StandardCharsets;
import org.agrona.DirectBuffer;

public class SubscribedRecordCollector implements ClientMessageHandler {
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final SubscribedRecordDecoder subscribedRecordDecoder = new SubscribedRecordDecoder();

  private final SubscribedEventHandler eventHandler;
  private final ZeebeObjectMapperImpl objectMapper;

  public SubscribedRecordCollector(
      SubscribedEventHandler eventHandler, ZeebeObjectMapperImpl objectMapper) {
    this.eventHandler = eventHandler;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean onMessage(
      ClientOutput output,
      RemoteAddress remoteAddress,
      DirectBuffer buffer,
      int offset,
      int length) {
    messageHeaderDecoder.wrap(buffer, offset);

    offset += MessageHeaderDecoder.ENCODED_LENGTH;

    final int templateId = messageHeaderDecoder.templateId();

    final boolean messageHandled;

    if (templateId == SubscribedRecordDecoder.TEMPLATE_ID) {

      subscribedRecordDecoder.wrap(
          buffer, offset, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

      final int partitionId = subscribedRecordDecoder.partitionId();
      final long position = subscribedRecordDecoder.position();
      final long sourceRecordPosition = subscribedRecordDecoder.sourceRecordPosition();
      final long key = subscribedRecordDecoder.key();
      final long subscriberKey = subscribedRecordDecoder.subscriberKey();
      final RecordType recordType = subscribedRecordDecoder.recordType();
      final SubscriptionType subscriptionType = subscribedRecordDecoder.subscriptionType();
      final ValueType valueType = subscribedRecordDecoder.valueType();
      final Intent intent = Intent.fromProtocolValue(valueType, subscribedRecordDecoder.intent());
      final long timestamp = subscribedRecordDecoder.timestamp();
      final RejectionType rejectionType = subscribedRecordDecoder.rejectionType();

      final byte[] valueBuffer =
          readBytes(subscribedRecordDecoder::getValue, subscribedRecordDecoder::valueLength);

      final int rejectionReasonLength = subscribedRecordDecoder.rejectionReasonLength();
      final String rejectionReason;
      if (rejectionReasonLength > 0) {
        rejectionReason =
            new String(
                readBytes(subscribedRecordDecoder::getRejectionReason, rejectionReasonLength),
                StandardCharsets.UTF_8);
      } else {
        rejectionReason = null;
      }

      final UntypedRecordImpl event =
          new UntypedRecordImpl(objectMapper, recordType, valueType, valueBuffer);

      event.setPartitionId(partitionId);
      event.setPosition(position);
      event.setKey(key);
      event.setSourceRecordPosition(sourceRecordPosition);
      event.setIntent(intent);
      event.setTimestamp(timestamp);
      event.setRejectionType(rejectionType);
      event.setRejectioReason(rejectionReason);

      messageHandled = eventHandler.onEvent(subscriptionType, subscriberKey, event);
    } else {
      // ignoring
      messageHandled = true;
    }

    return messageHandled;
  }
}
