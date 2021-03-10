/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.job;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobIntent;
import org.junit.Test;

public final class ThrowErrorTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final ThrowErrorStub stub = new ThrowErrorStub();
    stub.registerWith(brokerClient);

    final String errorCode = "test";

    final ThrowErrorRequest request =
        ThrowErrorRequest.newBuilder()
            .setJobKey(stub.getKey())
            .setErrorCode(errorCode)
            .setErrorMessage("failed")
            .build();

    // when
    final ThrowErrorResponse response = client.throwError(request);

    // then
    assertThat(response).isNotNull();

    final BrokerThrowErrorRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.THROW_ERROR);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getErrorCode()).isEqualTo(errorCode);
    assertThat(brokerRequestValue.getErrorMessageBuffer()).isEqualTo(wrapString("failed"));
  }
}
