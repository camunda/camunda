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
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyStatusRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyStatusIntent;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

/** Asks partition 1 for the committed policy and the open policy update. */
final class BrokerPolicyStatusRequest extends BrokerExecuteCommand<PolicyStatusRecord> {

  private final PolicyStatusRecord query = new PolicyStatusRecord();

  BrokerPolicyStatusRequest() {
    super(ValueType.POLICY_STATUS, PolicyStatusIntent.QUERY);
    setPartitionId(Protocol.DEPLOYMENT_PARTITION);
  }

  @Override
  public BufferWriter getRequestWriter() {
    return query;
  }

  @Override
  protected PolicyStatusRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new PolicyStatusRecord();
    response.wrap(buffer);
    return response;
  }
}
