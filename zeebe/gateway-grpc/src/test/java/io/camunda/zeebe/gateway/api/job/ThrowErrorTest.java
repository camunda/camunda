/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ThrowErrorResponse;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public final class ThrowErrorTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final ThrowErrorStub stub = new ThrowErrorStub();
    stub.registerWith(brokerClient);

    final String errorCode = "test";

    final String variables = JsonUtil.toJson(Collections.singletonMap("foo", "bar"));

    final ThrowErrorRequest request =
        ThrowErrorRequest.newBuilder()
            .setJobKey(stub.getKey())
            .setErrorCode(errorCode)
            .setErrorMessage("failed")
            .setVariables(variables)
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

    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariablesBuffer(), variables);
    assertThat(brokerRequestValue.getVariables().get("foo")).isEqualTo("bar");
  }
}
