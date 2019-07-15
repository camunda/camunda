/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.request;

import static io.zeebe.protocol.impl.record.value.deployment.DeploymentResource.getResourceType;

import io.zeebe.gateway.cmd.InvalidBrokerRequestArgumentException;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject.ResourceType;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
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

  private io.zeebe.protocol.record.value.deployment.ResourceType determineResourceType(
      String resourceName, ResourceType resourceType) {
    switch (resourceType) {
      case BPMN:
        return io.zeebe.protocol.record.value.deployment.ResourceType.BPMN_XML;
      case YAML:
        return io.zeebe.protocol.record.value.deployment.ResourceType.YAML_WORKFLOW;
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
