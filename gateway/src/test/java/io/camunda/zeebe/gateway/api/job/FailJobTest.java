/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.FailJobResponse;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.junit.Test;

public final class FailJobTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final FailJobStub stub = new FailJobStub();
    stub.registerWith(brokerClient);

    final int retries = 123;
    final int retryBackOff = 100;

    final FailJobRequest request =
        FailJobRequest.newBuilder()
            .setJobKey(stub.getKey())
            .setRetries(retries)
            .setRetryBackOff(retryBackOff)
            .setErrorMessage("failed")
            .build();

    // when
    final FailJobResponse response = client.failJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerFailJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.FAIL);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getRetries()).isEqualTo(retries);
    assertThat(brokerRequestValue.getRetryBackoff()).isEqualTo(retryBackOff);
    assertThat(brokerRequestValue.getErrorMessageBuffer()).isEqualTo(wrapString("failed"));
  }
}
