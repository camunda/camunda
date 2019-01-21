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

import static io.zeebe.protocol.impl.record.value.deployment.ResourceType.getResourceType;

import io.zeebe.gateway.cmd.InvalidBrokerRequestArgumentException;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject.ResourceType;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import org.agrona.DirectBuffer;

public class BrokerDeployWorkflowRequest extends BrokerExecuteCommand<DeploymentRecord> {

  private final DeploymentRecord requestDto = new DeploymentRecord();

  public BrokerDeployWorkflowRequest() {
    super(ValueType.DEPLOYMENT, DeploymentIntent.CREATE);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  public BrokerDeployWorkflowRequest addResource(
      final byte[] resource, final String resourceName, ResourceType resourceType) {
    requestDto
        .resources()
        .add()
        .setResource(resource)
        .setResourceName(resourceName)
        .setResourceType(determineResourceType(resourceName, resourceType));

    return this;
  }

  private io.zeebe.protocol.impl.record.value.deployment.ResourceType determineResourceType(
      String resourceName, ResourceType resourceType) {
    switch (resourceType) {
      case BPMN:
        return io.zeebe.protocol.impl.record.value.deployment.ResourceType.BPMN_XML;
      case YAML:
        return io.zeebe.protocol.impl.record.value.deployment.ResourceType.YAML_WORKFLOW;
      default:
        try {
          return getResourceType(resourceName);
        } catch (RuntimeException e) {
          throw new InvalidBrokerRequestArgumentException(
              "name", "a string ending with either .bpmn or .yaml", resourceName, e);
        }
    }
  }

  @Override
  public DeploymentRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected DeploymentRecord toResponseDto(DirectBuffer buffer) {
    final DeploymentRecord responseDto = new DeploymentRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
