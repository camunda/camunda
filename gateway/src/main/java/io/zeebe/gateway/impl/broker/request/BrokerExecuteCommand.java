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

import io.zeebe.gateway.cmd.UnsupportedBrokerResponseException;
import io.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.zeebe.gateway.impl.broker.response.BrokerRejectionResponse;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.zeebe.protocol.impl.encoding.ExecuteCommandResponse;
import io.zeebe.protocol.intent.Intent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public abstract class BrokerExecuteCommand<T> extends BrokerRequest<T> {

  protected final ExecuteCommandRequest request = new ExecuteCommandRequest();
  protected final ExecuteCommandResponse response = new ExecuteCommandResponse();

  public BrokerExecuteCommand(ValueType valueType, Intent intent) {
    super(ExecuteCommandResponseDecoder.SCHEMA_ID, ExecuteCommandResponseDecoder.TEMPLATE_ID);
    request.setValueType(valueType);
    request.setIntent(intent);
  }

  public long getKey() {
    return request.getKey();
  }

  public Intent getIntent() {
    return request.getIntent();
  }

  public ValueType getValueType() {
    return request.getValueType();
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
    return getPartitionId() != ExecuteCommandRequestEncoder.partitionIdNullValue();
  }

  @Override
  public boolean requiresPartitionId() {
    return true;
  }

  @Override
  protected void setSerializedValue(DirectBuffer buffer) {
    request.setValue(buffer, 0, buffer.capacity());
  }

  @Override
  public int getLength() {
    return request.getLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    request.write(buffer, offset);
  }

  @Override
  protected void wrapResponse(DirectBuffer buffer) {
    response.wrap(buffer, 0, buffer.capacity());
  }

  @Override
  protected BrokerResponse<T> readResponse() {
    if (isRejection()) {
      final BrokerRejection brokerRejection =
          new BrokerRejection(
              request.getIntent(),
              request.getKey(),
              response.getRejectionType(),
              response.getRejectionReason());
      return new BrokerRejectionResponse<>(brokerRejection);
    } else if (isValidValueType()) {
      final T responseDto = toResponseDto(response.getValue());
      return new BrokerResponse<>(responseDto, response.getPartitionId(), response.getKey());
    } else {
      throw new UnsupportedBrokerResponseException(
          request.getValueType().name(), response.getValueType().name());
    }
  }

  private boolean isValidValueType() {
    return response.getValueType() == request.getValueType();
  }

  protected boolean isRejection() {
    return response.getRecordType() == RecordType.COMMAND_REJECTION;
  }
}
