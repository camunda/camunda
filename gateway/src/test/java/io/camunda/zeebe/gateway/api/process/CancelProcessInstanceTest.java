/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.Test;

public final class CancelProcessInstanceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CancelProcessInstanceStub stub = new CancelProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CancelProcessInstanceRequest request =
        CancelProcessInstanceRequest.newBuilder().setProcessInstanceKey(123).build();

    // when
    final CancelProcessInstanceResponse response = client.cancelProcessInstance(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCancelProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(123);
    assertThat(brokerRequest.getIntent()).isEqualTo(ProcessInstanceIntent.CANCEL);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }
}
