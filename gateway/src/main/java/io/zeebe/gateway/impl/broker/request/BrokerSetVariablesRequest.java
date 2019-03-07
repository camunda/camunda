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

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import org.agrona.DirectBuffer;

public class BrokerSetVariablesRequest extends BrokerExecuteCommand<VariableDocumentRecord> {

  private final VariableDocumentRecord requestDto = new VariableDocumentRecord();

  public BrokerSetVariablesRequest() {
    super(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.UPDATE);
  }

  public BrokerSetVariablesRequest setElementInstanceKey(long elementInstanceKey) {
    request.setPartitionId(Protocol.decodePartitionId(elementInstanceKey));
    requestDto.setScopeKey(elementInstanceKey);
    return this;
  }

  public BrokerSetVariablesRequest setDocument(DirectBuffer document) {
    requestDto.setDocument(document);
    return this;
  }

  public BrokerSetVariablesRequest setLocal(boolean local) {
    final VariableDocumentUpdateSemantic updateSemantics =
        local ? VariableDocumentUpdateSemantic.LOCAL : VariableDocumentUpdateSemantic.PROPAGATE;

    requestDto.setUpdateSemantics(updateSemantics);
    return this;
  }

  @Override
  public VariableDocumentRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected VariableDocumentRecord toResponseDto(DirectBuffer buffer) {
    final VariableDocumentRecord responseDto = new VariableDocumentRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
