/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import org.agrona.DirectBuffer;

public final class BrokerUpdateManagedScriptDefinitionRequest
    extends BrokerExecuteCommand<ManagedScriptDefinitionRecord> {

  private final ManagedScriptDefinitionRecord requestDto = new ManagedScriptDefinitionRecord();

  public BrokerUpdateManagedScriptDefinitionRequest(
      final long definitionKey,
      final long revision,
      final String leaseToken,
      final String operationId,
      final ManagedScriptDefinitionStatus status) {
    super(ValueType.MANAGED_SCRIPT_DEFINITION, ManagedScriptDefinitionIntent.UPDATE);
    request.setKey(definitionKey);
    requestDto
        .setRevision(revision)
        .setLeaseToken(leaseToken)
        .setOperationId(operationId)
        .setStatus(status);
  }

  public BrokerUpdateManagedScriptDefinitionRequest setProviderOperationId(
      final String providerOperationId) {
    requestDto.setProviderOperationId(providerOperationId);
    return this;
  }

  public BrokerUpdateManagedScriptDefinitionRequest setProviderDeploymentId(
      final String providerDeploymentId) {
    requestDto.setProviderDeploymentId(providerDeploymentId);
    return this;
  }

  public BrokerUpdateManagedScriptDefinitionRequest setFailure(
      final String code, final String message, final boolean retryable) {
    requestDto.setFailureCode(code).setFailureMessage(message).setRetryable(retryable);
    return this;
  }

  @Override
  public ManagedScriptDefinitionRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ManagedScriptDefinitionRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new ManagedScriptDefinitionRecord();
    response.wrap(buffer);
    return response;
  }
}
