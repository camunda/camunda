/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.workflow;

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerCancelWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;

public class CancelWorkflowInstanceStub
    implements RequestStub<
        BrokerCancelWorkflowInstanceRequest, BrokerResponse<WorkflowInstanceRecord>> {

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerCancelWorkflowInstanceRequest.class, this);
  }

  @Override
  public BrokerResponse<WorkflowInstanceRecord> handle(BrokerCancelWorkflowInstanceRequest request)
      throws Exception {
    return new BrokerResponse<>(
        new WorkflowInstanceRecord(), request.getPartitionId(), request.getKey());
  }
}
