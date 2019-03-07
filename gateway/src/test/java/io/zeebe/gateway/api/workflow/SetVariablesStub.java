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
package io.zeebe.gateway.api.workflow;

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;

public class SetVariablesStub
    implements RequestStub<BrokerSetVariablesRequest, BrokerResponse<VariableDocumentRecord>> {

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerSetVariablesRequest.class, this);
  }

  @Override
  public BrokerResponse<VariableDocumentRecord> handle(BrokerSetVariablesRequest request)
      throws Exception {
    return new BrokerResponse<>(
        new VariableDocumentRecord(), request.getPartitionId(), request.getKey());
  }
}
