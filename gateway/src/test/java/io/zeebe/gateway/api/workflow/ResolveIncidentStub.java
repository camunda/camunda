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

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import org.agrona.DirectBuffer;

public class ResolveIncidentStub
    implements RequestStub<BrokerResolveIncidentRequest, BrokerResponse<IncidentRecord>> {

  public static final long WORKFLOW_INSTANCE_KEY = 123;
  public static final long INCIDENT_KEY = 11;
  public static final DirectBuffer PROCESS_ID = wrapString("process");

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerResolveIncidentRequest.class, this);
  }

  public long getIncidentKey() {
    return INCIDENT_KEY;
  }

  @Override
  public BrokerResponse<IncidentRecord> handle(BrokerResolveIncidentRequest request) {

    final IncidentRecord response = new IncidentRecord();
    response.setElementInstanceKey(WORKFLOW_INSTANCE_KEY);
    response.setWorkflowInstanceKey(WORKFLOW_INSTANCE_KEY);
    response.setElementId(PROCESS_ID);
    response.setBpmnProcessId(PROCESS_ID);
    response.setErrorMessage("Error in IO mapping");
    response.setErrorType(ErrorType.IO_MAPPING_ERROR);

    return new BrokerResponse<>(response, 0, WORKFLOW_INSTANCE_KEY);
  }
}
