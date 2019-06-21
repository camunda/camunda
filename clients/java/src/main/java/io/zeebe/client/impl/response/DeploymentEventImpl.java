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
package io.zeebe.client.impl.response;

import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.Workflow;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import java.util.List;
import java.util.stream.Collectors;

public class DeploymentEventImpl implements DeploymentEvent {

  private final long key;
  private final List<Workflow> workflows;

  public DeploymentEventImpl(final DeployWorkflowResponse response) {
    key = response.getKey();
    workflows =
        response.getWorkflowsList().stream().map(WorkflowImpl::new).collect(Collectors.toList());
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public List<Workflow> getWorkflows() {
    return workflows;
  }

  @Override
  public String toString() {
    return "DeploymentEventImpl{" + "key=" + key + ", workflows=" + workflows + '}';
  }
}
