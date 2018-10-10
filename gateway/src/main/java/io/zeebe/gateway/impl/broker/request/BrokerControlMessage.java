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

import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.clientapi.ControlMessageRequestEncoder;
import io.zeebe.protocol.clientapi.ControlMessageResponseDecoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.encoding.ControlMessageRequest;
import io.zeebe.protocol.impl.encoding.ControlMessageResponse;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public abstract class BrokerControlMessage<T> extends BrokerRequest<T> {

  protected final ControlMessageRequest request = new ControlMessageRequest();
  protected final ControlMessageResponse response = new ControlMessageResponse();

  public BrokerControlMessage(ControlMessageType messageType) {
    super(ControlMessageResponseDecoder.SCHEMA_ID, ControlMessageResponseDecoder.TEMPLATE_ID);
    request.setMessageType(messageType);
  }

  public ControlMessageType getMessageType() {
    return request.getMessageType();
  }

  @Override
  public int getPartitionId() {
    return request.getPartitionId();
  }

  @Override
  public void setPartitionId(int partitionId) {
    request.setPartitionId(partitionId);
  }

  @Override
  public boolean addressesSpecificPartition() {
    return getPartitionId() != ControlMessageRequestEncoder.partitionIdNullValue();
  }

  @Override
  public boolean requiresPartitionId() {
    return false;
  }

  @Override
  protected void setSerializedValue(DirectBuffer buffer) {
    request.setData(buffer, 0, buffer.capacity());
  }

  @Override
  public int getLength() {
    return request.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    request.write(buffer, offset);
  }

  protected void wrapResponse(DirectBuffer buffer) {
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<T> readResponse() {
    final T responseDto = toResponseDto(response.getData());
    return new BrokerResponse<>(responseDto);
  }
}
