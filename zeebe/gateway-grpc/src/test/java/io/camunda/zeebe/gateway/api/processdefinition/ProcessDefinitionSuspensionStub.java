/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.processdefinition;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;

/**
 * Answers the suspension or resumption of process definitions, depending on the broker request
 * type, with a fixed response unless told otherwise.
 */
public final class ProcessDefinitionSuspensionStub<
        T extends BrokerExecuteCommand<ProcessDefinitionSuspensionRecord>>
    implements RequestStub<T, BrokerResponse<ProcessDefinitionSuspensionRecord>> {

  private final Class<T> requestType;
  private final ProcessDefinitionSuspensionRecord record;
  private BrokerResponse<ProcessDefinitionSuspensionRecord> response;

  public ProcessDefinitionSuspensionStub(
      final Class<T> requestType, final ProcessDefinitionSuspensionRecord record) {
    this.requestType = requestType;
    this.record = record;
  }

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(requestType, this);
  }

  public ProcessDefinitionSuspensionStub<T> respondWith(
      final BrokerResponse<ProcessDefinitionSuspensionRecord> response) {
    this.response = response;
    return this;
  }

  @Override
  public BrokerResponse<ProcessDefinitionSuspensionRecord> handle(final T request) {
    if (response != null) {
      return response;
    }
    return new BrokerResponse<>(record, request.getPartitionId(), request.getKey());
  }
}
