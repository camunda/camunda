/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobTimeoutRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutResponse;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.junit.Test;

public class UpdateJobTimeoutTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final UpdateJobTimeoutStub stub = new UpdateJobTimeoutStub();
    stub.registerWith(brokerClient);

    final long timeout = 100000;

    final UpdateJobTimeoutRequest request =
        UpdateJobTimeoutRequest.newBuilder().setJobKey(stub.getKey()).setTimeout(timeout).build();

    // when
    final UpdateJobTimeoutResponse response = client.updateJobTimeout(request);

    // then
    assertThat(response).isNotNull();

    final BrokerUpdateJobTimeoutRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.UPDATE_TIMEOUT);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getTimeout()).isEqualTo(timeout);
  }
}
