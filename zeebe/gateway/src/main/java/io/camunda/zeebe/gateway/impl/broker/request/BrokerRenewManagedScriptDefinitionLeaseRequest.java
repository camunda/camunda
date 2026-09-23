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
import org.agrona.DirectBuffer;

public final class BrokerRenewManagedScriptDefinitionLeaseRequest
    extends BrokerExecuteCommand<ManagedScriptDefinitionRecord> {

  private final ManagedScriptDefinitionRecord requestDto = new ManagedScriptDefinitionRecord();

  public BrokerRenewManagedScriptDefinitionLeaseRequest(
      final long definitionKey,
      final long revision,
      final String leaseToken,
      final long leaseDuration) {
    super(ValueType.MANAGED_SCRIPT_DEFINITION, ManagedScriptDefinitionIntent.RENEW_LEASE);
    request.setKey(definitionKey);
    requestDto.setRevision(revision).setLeaseToken(leaseToken).setLeaseDuration(leaseDuration);
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
