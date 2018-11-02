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
import io.zeebe.gateway.impl.broker.request.BrokerDeployWorkflowRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;

public class DeployWorkflowStub
    implements RequestStub<BrokerDeployWorkflowRequest, BrokerResponse<DeploymentRecord>> {

  private static final long KEY = 123;
  private static final long WORKFLOW_KEY = 456;
  private static final int WORKFLOW_VERSION = 789;

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerDeployWorkflowRequest.class, this);
  }

  protected long getKey() {
    return KEY;
  }

  protected long getWorkflowKey() {
    return WORKFLOW_KEY;
  }

  public int getWorkflowVersion() {
    return WORKFLOW_VERSION;
  }

  @Override
  public BrokerResponse<DeploymentRecord> handle(BrokerDeployWorkflowRequest request)
      throws Exception {
    final DeploymentRecord deploymentRecord = request.getRequestWriter();
    deploymentRecord
        .resources()
        .iterator()
        .forEachRemaining(
            r -> {
              deploymentRecord
                  .workflows()
                  .add()
                  .setBpmnProcessId(r.getResourceName())
                  .setResourceName(r.getResourceName())
                  .setVersion(WORKFLOW_VERSION)
                  .setKey(WORKFLOW_KEY);
            });
    return new BrokerResponse<>(deploymentRecord, 0, KEY);
  }
}
