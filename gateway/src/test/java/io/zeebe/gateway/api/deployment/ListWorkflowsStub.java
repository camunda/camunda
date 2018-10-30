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
import io.zeebe.gateway.impl.broker.request.BrokerListWorkflowsRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.data.repository.ListWorkflowsResponse;
import java.util.Arrays;
import java.util.List;

public class ListWorkflowsStub
    implements RequestStub<BrokerListWorkflowsRequest, BrokerResponse<ListWorkflowsResponse>> {

  private static final List<Workflow> WORKFLOWS =
      Arrays.asList(
          new Workflow(123, "testProcess", 12, "foobar.bpmn"),
          new Workflow(11, "demoProcess", 563, "demo.bpmn"),
          new Workflow(84, "yamlProcess", 3, "process.yaml"));

  public List<Workflow> getWorkflows() {
    return WORKFLOWS;
  }

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerListWorkflowsRequest.class, this);
  }

  @Override
  public BrokerResponse<ListWorkflowsResponse> handle(BrokerListWorkflowsRequest request)
      throws Exception {
    final ListWorkflowsResponse listWorkflowsResponse = new ListWorkflowsResponse();
    WORKFLOWS.forEach(
        wf ->
            listWorkflowsResponse
                .getWorkflows()
                .add()
                .setWorkflowKey(wf.workflowKey)
                .setBpmnProcessId(wf.bpmnProcessId)
                .setVersion(wf.version)
                .setResourceName(wf.resourceName));
    return new BrokerResponse<>(listWorkflowsResponse);
  }

  public static class Workflow {
    private final long workflowKey;
    private final String bpmnProcessId;
    private final int version;
    private final String resourceName;

    public Workflow(long workflowKey, String bpmnProcessId, int version, String resourceName) {
      this.workflowKey = workflowKey;
      this.bpmnProcessId = bpmnProcessId;
      this.version = version;
      this.resourceName = resourceName;
    }

    public long getWorkflowKey() {
      return workflowKey;
    }

    public String getBpmnProcessId() {
      return bpmnProcessId;
    }

    public int getVersion() {
      return version;
    }

    public String getResourceName() {
      return resourceName;
    }
  }
}
