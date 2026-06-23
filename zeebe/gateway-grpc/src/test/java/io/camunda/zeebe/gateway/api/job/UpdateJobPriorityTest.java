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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobPriorityRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobPriorityResponse;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.junit.Test;

public class UpdateJobPriorityTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final UpdateJobPriorityStub stub = new UpdateJobPriorityStub();
    stub.registerWith(brokerClient);
    final long operationReference = 123L;
    final int priority = 5;
    final UpdateJobPriorityRequest request =
        UpdateJobPriorityRequest.newBuilder()
            .setJobKey(stub.getKey())
            .setPriority(priority)
            .setOperationReference(operationReference)
            .build();

    // when
    final UpdateJobPriorityResponse response = client.updateJobPriority(request);

    // then
    assertThat(response).isNotNull();
    final BrokerUpdateJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());
    assertThat(brokerRequest.getIntent()).isEqualTo(JobIntent.UPDATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.JOB);
    final JobRecord brokerRequestValue = (JobRecord) brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getPriority()).isEqualTo(priority);
    assertThat(brokerRequest.getOperationReference()).isEqualTo(operationReference);
    assertThat(brokerRequestValue.getChangedAttributes()).contains(JobRecord.PRIORITY);
  }
}
