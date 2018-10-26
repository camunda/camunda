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
import io.zeebe.gateway.impl.broker.request.BrokerUpdateWorkflowInstancePayloadRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;

public class UpdatePayloadStub
    implements RequestStub<
        BrokerUpdateWorkflowInstancePayloadRequest, BrokerResponse<WorkflowInstanceRecord>> {

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerUpdateWorkflowInstancePayloadRequest.class, this);
  }

  @Override
  public BrokerResponse<WorkflowInstanceRecord> handle(
      BrokerUpdateWorkflowInstancePayloadRequest request) throws Exception {
    return new BrokerResponse<>(
        new WorkflowInstanceRecord(), request.getPartitionId(), request.getKey());
  }
}
