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
package io.zeebe.gateway.impl.broker.request;

import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.intent.MessageIntent;
import org.agrona.DirectBuffer;

public class BrokerPublishMessageRequest extends BrokerExecuteCommand<Void> {

  private final MessageRecord requestDto = new MessageRecord();

  public BrokerPublishMessageRequest(String messageName, String correlationKey) {
    super(ValueType.MESSAGE, MessageIntent.PUBLISH);
    requestDto.setName(messageName).setCorrelationKey(correlationKey);
  }

  public DirectBuffer getCorrelationKey() {
    return requestDto.getCorrelationKey();
  }

  public BrokerPublishMessageRequest setMessageId(String messageId) {
    requestDto.setMessageId(messageId);
    return this;
  }

  public BrokerPublishMessageRequest setTimeToLive(long timeToLive) {
    requestDto.setTimeToLive(timeToLive);
    return this;
  }

  public BrokerPublishMessageRequest setVariables(DirectBuffer variables) {
    requestDto.setVariables(variables);
    return this;
  }

  @Override
  public MessageRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected Void toResponseDto(DirectBuffer buffer) {
    return null;
  }
}
