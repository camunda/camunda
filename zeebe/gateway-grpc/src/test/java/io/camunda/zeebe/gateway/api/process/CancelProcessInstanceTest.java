/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CancelProcessInstanceResponse;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeoutException;
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

  @Test
  public void shouldMapTimeoutToDeadlineExceeded() {
    // given
    brokerClient.registerHandler(
        BrokerCancelProcessInstanceRequest.class,
        request -> {
          throw new TimeoutException("request timeout");
        });

    final CancelProcessInstanceRequest request =
        CancelProcessInstanceRequest.newBuilder().setProcessInstanceKey(123).build();

    // when / then
    assertThatThrownBy(() -> client.cancelProcessInstance(request))
        .isInstanceOf(StatusRuntimeException.class)
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.THROWABLE)
        .extracting(
            t -> ((StatusRuntimeException) t).getStatus().getCode(),
            t -> ((StatusRuntimeException) t).getStatus().getDescription())
        .containsExactly(
            Status.Code.DEADLINE_EXCEEDED, "Time out between gateway and broker: request timeout");
  }
}
