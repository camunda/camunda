/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public final class CompleteJobTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setVariables(variables).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.COMPLETE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariablesBuffer(), variables);
  }

  @Test
  public void shouldConvertEmptyVariables() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setVariables("").build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariablesBuffer(), "{}");
  }
}
