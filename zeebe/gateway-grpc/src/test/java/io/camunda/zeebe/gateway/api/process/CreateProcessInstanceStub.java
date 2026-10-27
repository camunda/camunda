/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import java.util.Set;

public final class CreateProcessInstanceStub
    implements RequestStub<
        BrokerCreateProcessInstanceRequest, BrokerResponse<ProcessInstanceCreationRecord>> {

  public static final long PROCESS_INSTANCE_KEY = 123;
  public static final String PROCESS_ID = "process";
  public static final int PROCESS_VERSION = 1;
  public static final long PROCESS_KEY = 456;
  public static final Set<String> TAGS = Set.of("tag1", "tag2");

  private BrokerResponse<ProcessInstanceCreationRecord> response;

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerCreateProcessInstanceRequest.class, this);
  }

  public CreateProcessInstanceStub respondWith(
      final BrokerResponse<ProcessInstanceCreationRecord> response) {
    this.response = response;
    return this;
  }

  public long getProcessInstanceKey() {
    return PROCESS_INSTANCE_KEY;
  }

  public String getProcessId() {
    return PROCESS_ID;
  }

  public int getProcessVersion() {
    return PROCESS_VERSION;
  }

  public long getProcessDefinitionKey() {
    return PROCESS_KEY;
  }

  public Set<String> getTags() {
    return TAGS;
  }

  @Override
  public BrokerResponse<ProcessInstanceCreationRecord> handle(
      final BrokerCreateProcessInstanceRequest request) {

    if (response != null) {
      return response;
    }

    return getDefaultResponse(request);
  }

  private BrokerResponse<ProcessInstanceCreationRecord> getDefaultResponse(
      final BrokerCreateProcessInstanceRequest request) {
    final var piCreationRecord = request.getRequestWriter();
    final var record = new ProcessInstanceCreationRecord();
    record
        .setBpmnProcessId(PROCESS_ID)
        .setVariables(piCreationRecord.getVariablesBuffer())
        .setVersion(PROCESS_VERSION)
        .setTenantId(piCreationRecord.getTenantId())
        .setProcessDefinitionKey(PROCESS_KEY)
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setTags(TAGS)
        .setBusinessId(piCreationRecord.getBusinessId());

    return new BrokerResponse<>(record, 0, PROCESS_INSTANCE_KEY);
  }
}
