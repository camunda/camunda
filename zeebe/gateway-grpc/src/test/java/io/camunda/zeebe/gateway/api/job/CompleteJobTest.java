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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResult;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResultCorrections;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StringList;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import java.util.List;
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

  @Test
  public void shouldMapRequestAndResponseWithResultSetDeniedTrue() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final JobResult jobResult = JobResult.newBuilder().setDenied(true).build();

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setResult(jobResult).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord jobRecord = brokerRequest.getRequestWriter();

    assertThat(jobRecord.getResult().isDenied()).isTrue();
  }

  @Test
  public void shouldMapRequestAndResponseWithResultSetDeniedFalse() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final JobResult jobResult = JobResult.newBuilder().setDenied(false).build();

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setResult(jobResult).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord jobRecord = brokerRequest.getRequestWriter();

    assertThat(jobRecord.getResult().isDenied()).isFalse();
  }

  @Test
  public void shouldMapRequestAndResponseWithResultDeniedNotSet() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final JobResult jobResult = JobResult.newBuilder().build();

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setResult(jobResult).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();

    assertThat(brokerRequestValue.getResult().isDenied()).isFalse();

    // check default values
    assertThat(brokerRequestValue.getResult().getCorrections().getAssignee()).isEqualTo("");
    assertThat(brokerRequestValue.getResult().getCorrections().getDueDate()).isEqualTo("");
    assertThat(brokerRequestValue.getResult().getCorrections().getFollowUpDate()).isEqualTo("");
    assertThat(brokerRequestValue.getResult().getCorrections().getCandidateUsers())
        .isEqualTo(List.of());
    assertThat(brokerRequestValue.getResult().getCorrections().getCandidateGroups())
        .isEqualTo(List.of());
    assertThat(brokerRequestValue.getResult().getCorrections().getPriority()).isEqualTo(-1);

    assertThat(brokerRequestValue.getResult().getCorrectedAttributes()).isEqualTo(List.of());
  }

  @Test
  public void shouldMapRequestAndResponseWithResultCorrectionsFullySet() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final JobResultCorrections corrections =
        JobResultCorrections.newBuilder()
            .setAssignee("Assignee")
            .setDueDate("2025-05-23T01:02:03+01:00")
            .setFollowUpDate("2025-06-23T01:02:03+01:00")
            .setCandidateUsersList(
                StringList.newBuilder().addAllValues(List.of("User A, User B")).build())
            .setCandidateGroupsList(
                StringList.newBuilder().addAllValues(List.of("Group A", "group B")).build())
            .setPriority(20)
            .build();

    final List<String> correctedAttributes =
        List.of(
            "assignee",
            "dueDate",
            "followUpDate",
            "candidateUsersList",
            "candidateGroupsList",
            "priority");

    final JobResult jobResult = JobResult.newBuilder().setCorrections(corrections).build();

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setResult(jobResult).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();

    verifyJobResultCorrections(corrections, correctedAttributes, brokerRequestValue);
  }

  @Test
  public void shouldMapRequestAndResponseWithResultCorrectionsPartiallySet() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final JobResultCorrections corrections =
        JobResultCorrections.newBuilder()
            .setAssignee("Assignee")
            .setDueDate("2025-05-23T01:02:03+01:00")
            .setFollowUpDate("2025-06-23T01:02:03+01:00")
            .setPriority(20)
            .build();

    final List<String> correctedAttributes =
        List.of("assignee", "dueDate", "followUpDate", "priority");

    final JobResult jobResult = JobResult.newBuilder().setCorrections(corrections).build();

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setResult(jobResult).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();

    verifyJobResultCorrections(corrections, correctedAttributes, brokerRequestValue);
  }

  private static void verifyJobResultCorrections(
      final JobResultCorrections corrections,
      final List<String> correctedAttributes,
      final JobRecord brokerRequestValue) {
    assertThat(brokerRequestValue.getResult().getCorrections().getAssignee())
        .isEqualTo(corrections.getAssignee());
    assertThat(brokerRequestValue.getResult().getCorrections().getDueDate())
        .isEqualTo(corrections.getDueDate());
    assertThat(brokerRequestValue.getResult().getCorrections().getFollowUpDate())
        .isEqualTo(corrections.getFollowUpDate());
    assertThat(brokerRequestValue.getResult().getCorrections().getCandidateUsers())
        .isEqualTo(corrections.getCandidateUsersList().getValuesList());
    assertThat(brokerRequestValue.getResult().getCorrections().getCandidateGroups())
        .isEqualTo(corrections.getCandidateGroupsList().getValuesList());
    assertThat(brokerRequestValue.getResult().getCorrections().getPriority())
        .isEqualTo(corrections.getPriority());

    assertThat(brokerRequestValue.getResult().getCorrectedAttributes())
        .isEqualTo(correctedAttributes);
  }
}
