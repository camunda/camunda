/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.hub;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/** Appends a chunk of a policy update on partition 1, which coordinates every policy update. */
final class BrokerPolicyUpdateRequest extends BrokerExecuteCommand<PolicyUpdateRecord> {

  private final PolicyUpdateRecord chunk;

  BrokerPolicyUpdateRequest(final PolicyUpdateRecord chunk) {
    super(ValueType.POLICY_UPDATE, PolicyUpdateIntent.APPEND_CHUNK);
    this.chunk = chunk;
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return chunk;
  }

  @Override
  protected PolicyUpdateRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new PolicyUpdateRecord();
    response.wrap(buffer);
    return response;
  }
}
