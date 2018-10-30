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
package io.zeebe.gateway.api.deployment;

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerGetWorkflowRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.data.repository.WorkflowMetadataAndResource;

public class GetWorkflowStub
    implements RequestStub<BrokerGetWorkflowRequest, BrokerResponse<WorkflowMetadataAndResource>> {

  private static final long WORKFLOW_KEY = 123L;
  private static final String BPMN_PROCESS_ID = "testProcess";
  private static final int VERSION = 394;
  private static final String RESOURCE_NAME = "testProcess.bpmn";
  private static final String BPMN_XML = "<?xml?>";

  public long getWorkflowKey() {
    return WORKFLOW_KEY;
  }

  public String getBpmnProcessId() {
    return BPMN_PROCESS_ID;
  }

  public int getVersion() {
    return VERSION;
  }

  public String getResourceName() {
    return RESOURCE_NAME;
  }

  public String getBpmnXml() {
    return BPMN_XML;
  }

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerGetWorkflowRequest.class, this);
  }

  @Override
  public BrokerResponse<WorkflowMetadataAndResource> handle(BrokerGetWorkflowRequest request)
      throws Exception {
    final WorkflowMetadataAndResource response = new WorkflowMetadataAndResource();
    response.setWorkflowKey(WORKFLOW_KEY);
    response.setBpmnProcessId(BPMN_PROCESS_ID);
    response.setVersion(VERSION);
    response.setResourceName(RESOURCE_NAME);
    response.setBpmnXml(BPMN_XML);
    return new BrokerResponse<>(response);
  }
}
