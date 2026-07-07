/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSuspendResumeProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SuspendResumeProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SuspendResumeProcessInstanceResponse;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.junit.Test;

/**
 * Quick-and-dirty gRPC command for benchmarking the process instance suspend/resume POC (#56552)
 * against a real cluster (track (c)) — not a reviewed public API surface. Verifies the full
 * in-process round trip (gRPC stub call -&gt; {@code GatewayGrpcService} -&gt; {@code
 * EndpointManager} -&gt; mapper -&gt; {@code BrokerSuspendResumeProcessInstanceRequest}) without
 * needing a real cluster, mirroring {@link CancelProcessInstanceTest}.
 */
public final class SuspendResumeProcessInstanceTest extends GatewayTest {

  @Test
  public void shouldMapSuspendRequest() {
    // given
    final SuspendResumeProcessInstanceStub stub = new SuspendResumeProcessInstanceStub();
    stub.registerWith(brokerClient);

    final SuspendResumeProcessInstanceRequest request =
        SuspendResumeProcessInstanceRequest.newBuilder()
            .setProcessInstanceKey(123)
            .setResume(false)
            .build();

    // when
    final SuspendResumeProcessInstanceResponse response =
        client.suspendResumeProcessInstance(request);

    // then
    assertThat(response).isNotNull();

    final BrokerSuspendResumeProcessInstanceRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(123);
    assertThat(brokerRequest.getIntent()).isEqualTo(ProcessInstanceIntent.SUSPEND);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void shouldMapResumeRequest() {
    // given
    final SuspendResumeProcessInstanceStub stub = new SuspendResumeProcessInstanceStub();
    stub.registerWith(brokerClient);

    final SuspendResumeProcessInstanceRequest request =
        SuspendResumeProcessInstanceRequest.newBuilder()
            .setProcessInstanceKey(456)
            .setResume(true)
            .build();

    // when
    final SuspendResumeProcessInstanceResponse response =
        client.suspendResumeProcessInstance(request);

    // then
    assertThat(response).isNotNull();

    final BrokerSuspendResumeProcessInstanceRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(456);
    assertThat(brokerRequest.getIntent()).isEqualTo(ProcessInstanceIntent.RESUME);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }
}
