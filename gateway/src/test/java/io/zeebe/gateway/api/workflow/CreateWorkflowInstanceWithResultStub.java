/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.workflow;

import io.zeebe.gateway.api.util.StubbedBrokerClient;
import io.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceWithResultRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;

public final class CreateWorkflowInstanceWithResultStub
    implements RequestStub<
        BrokerCreateWorkflowInstanceWithResultRequest,
        BrokerResponse<WorkflowInstanceResultRecord>> {

  public static final long WORKFLOW_INSTANCE_KEY = 123;
  public static final String PROCESS_ID = "process";
  public static final int PROCESS_VERSION = 1;
  public static final long WORKFLOW_KEY = 456;

  @Override
  public void registerWith(final StubbedBrokerClient brokerClient) {
    brokerClient.registerHandler(BrokerCreateWorkflowInstanceWithResultRequest.class, this);
  }

  public long getWorkflowInstanceKey() {
    return WORKFLOW_INSTANCE_KEY;
  }

  public String getProcessId() {
    return PROCESS_ID;
  }

  public int getProcessVersion() {
    return PROCESS_VERSION;
  }

  public long getWorkflowKey() {
    return WORKFLOW_KEY;
  }

  @Override
  public BrokerResponse<WorkflowInstanceResultRecord> handle(
      final BrokerCreateWorkflowInstanceWithResultRequest request) throws Exception {
    final WorkflowInstanceResultRecord response = new WorkflowInstanceResultRecord();
    response.setBpmnProcessId(PROCESS_ID);
    response.setVariables(request.getRequestWriter().getVariablesBuffer());
    response.setVersion(PROCESS_VERSION);
    response.setWorkflowKey(WORKFLOW_KEY);
    response.setWorkflowInstanceKey(WORKFLOW_INSTANCE_KEY);

    return new BrokerResponse<>(response, 0, WORKFLOW_INSTANCE_KEY);
  }
}
