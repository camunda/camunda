/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public class BrokerCreateClusterVariableRequest
    extends BrokerExecuteCommand<ClusterVariableRecord> {

  private final ClusterVariableRecord requestDto = new ClusterVariableRecord();

  public BrokerCreateClusterVariableRequest() {
    super(ValueType.CLUSTER_VARIABLE, ClusterVariableIntent.CREATE);
  }

  public BrokerCreateClusterVariableRequest setGlobalScope() {
    requestDto.setScope(ClusterVariableScope.GLOBAL);
    return this;
  }

  public BrokerCreateClusterVariableRequest setTenantScope(final String tenantId) {
    requestDto.setScope(ClusterVariableScope.TENANT).setTenantId(tenantId);
    return this;
  }

  public BrokerCreateClusterVariableRequest setName(final String name) {
    requestDto.setName(name);
    return this;
  }

  public BrokerCreateClusterVariableRequest setValue(final DirectBuffer value) {
    requestDto.setValue(value);
    return this;
  }

  @Override
  public BufferWriter getRequestWriter() {
    return requestDto;
  }

  @Override
  protected ClusterVariableRecord toResponseDto(final DirectBuffer buffer) {
    final ClusterVariableRecord responseDto = new ClusterVariableRecord();
    responseDto.wrap(buffer);
    return responseDto;
  }
}
