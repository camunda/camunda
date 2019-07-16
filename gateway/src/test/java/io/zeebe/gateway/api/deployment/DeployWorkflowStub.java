/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
                  .setBpmnProcessId(r.getResourceNameBuffer())
                  .setResourceName(r.getResourceNameBuffer())
                  .setVersion(WORKFLOW_VERSION)
                  .setKey(WORKFLOW_KEY);
            });
    return new BrokerResponse<>(deploymentRecord, 0, KEY);
  }
}
