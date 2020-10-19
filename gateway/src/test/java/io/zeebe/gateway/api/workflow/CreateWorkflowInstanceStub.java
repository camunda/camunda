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
import io.zeebe.gateway.impl.broker.request.BrokerCreateWorkflowInstanceRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;

public final class CreateWorkflowInstanceStub
    implements RequestStub<
        BrokerCreateWorkflowInstanceRequest, BrokerResponse<WorkflowInstanceCreationRecord>> {

  public static final long WORKFLOW_INSTANCE_KEY = 123;
  public static final String PROCESS_ID = "process";
  public static final int PROCESS_VERSION = 1;
  public static final long WORKFLOW_KEY = 456;
  private BrokerResponse<WorkflowInstanceCreationRecord> response;

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerCreateWorkflowInstanceRequest.class, this);
  }

  public CreateWorkflowInstanceStub respondWith(
      final BrokerResponse<WorkflowInstanceCreationRecord> response) {
    this.response = response;
    return this;
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
  public BrokerResponse<WorkflowInstanceCreationRecord> handle(
      final BrokerCreateWorkflowInstanceRequest request) {

    if (response != null) {
      return response;
    }

    return getDefaultResponse(request);
  }

  private BrokerResponse<WorkflowInstanceCreationRecord> getDefaultResponse(
      final BrokerCreateWorkflowInstanceRequest request) {
    final var record = new WorkflowInstanceCreationRecord();
    record.setBpmnProcessId(PROCESS_ID);
    record.setVariables(request.getRequestWriter().getVariablesBuffer());
    record.setVersion(PROCESS_VERSION);
    record.setWorkflowKey(WORKFLOW_KEY);
    record.setWorkflowInstanceKey(WORKFLOW_INSTANCE_KEY);
    return new BrokerResponse<>(record, 0, WORKFLOW_INSTANCE_KEY);
  }
}
