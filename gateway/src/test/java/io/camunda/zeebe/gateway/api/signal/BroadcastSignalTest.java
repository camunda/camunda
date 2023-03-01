/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerBroadcastSignalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalResponse;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public class BroadcastSignalTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final BroadcastSignalStub stub = new BroadcastSignalStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final BroadcastSignalRequest request =
        BroadcastSignalRequest.newBuilder().setSignalName("signal").setVariables(variables).build();

    // when
    final BroadcastSignalResponse response = client.broadcastSignal(request);

    // then
    assertThat(response).isNotNull();

    final BrokerBroadcastSignalRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(SignalIntent.BROADCAST);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.SIGNAL);

    final SignalRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getSignalName()).isEqualTo(request.getSignalName());
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariablesBuffer(), variables);
  }
}
