/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelWorkflowInstanceResponse;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.junit.Test;

public final class CancelWorkflowInstanceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CancelWorkflowInstanceStub stub = new CancelWorkflowInstanceStub();
    stub.registerWith(brokerClient);

    final CancelWorkflowInstanceRequest request =
        CancelWorkflowInstanceRequest.newBuilder().setWorkflowInstanceKey(123).build();

    // when
    final CancelWorkflowInstanceResponse response = client.cancelWorkflowInstance(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCancelWorkflowInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(123);
    assertThat(brokerRequest.getIntent()).isEqualTo(WorkflowInstanceIntent.CANCEL);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE);
  }
}
