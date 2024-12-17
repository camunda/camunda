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
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StringList;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
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
    assertThat(brokerRequestValue.getResult().getCorrections().getCandidateUsersList())
        .isEqualTo(List.of());
    assertThat(brokerRequestValue.getResult().getCorrections().getCandidateGroupsList())
        .isEqualTo(List.of());
    assertThat(brokerRequestValue.getResult().getCorrections().getPriority()).isEqualTo(-1);

    assertThat(brokerRequestValue.getResult().getCorrectedAttributes()).isEqualTo(List.of());
  }

  @Test
  public void shouldMapRequestAndResponseWithResultCorrectionsFullySet() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final JobResult jobResult =
        JobResult.newBuilder()
            .setCorrections(
                io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResultCorrections
                    .newBuilder()
                    .setAssignee("Assignee")
                    .setDueDate("2025-05-23T01:02:03+01:00")
                    .setFollowUpDate("2025-06-23T01:02:03+01:00")
                    .setCandidateUsers(
                        StringList.newBuilder().addAllValues(List.of("User A, User B")).build())
                    .setCandidateGroups(
                        StringList.newBuilder().addAllValues(List.of("Group A", "group B")).build())
                    .setPriority(20)
                    .build())
            .build();

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setResult(jobResult).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();

    final JobResultCorrections expectedCorrections =
        new JobResultCorrections()
            .setAssignee("Assignee")
            .setDueDate("2025-05-23T01:02:03+01:00")
            .setFollowUpDate("2025-06-23T01:02:03+01:00")
            .setCandidateUsersList(List.of("User A, User B"))
            .setCandidateGroupsList(List.of("Group A", "group B"))
            .setPriority(20);

    final List<String> expectedCorrectedAttributes =
        List.of(
            UserTaskRecord.ASSIGNEE,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE,
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.PRIORITY);

    verifyJobResultCorrections(
        expectedCorrections, expectedCorrectedAttributes, brokerRequestValue);
  }

  @Test
  public void shouldMapRequestAndResponseWithResultCorrectionsPartiallySet() {
    // given
    final CompleteJobStub stub = new CompleteJobStub();
    stub.registerWith(brokerClient);

    final JobResult jobResult =
        JobResult.newBuilder()
            .setCorrections(
                io.camunda.zeebe.gateway.protocol.GatewayOuterClass.JobResultCorrections
                    .newBuilder()
                    .setAssignee("Assignee")
                    .setDueDate("2025-05-23T01:02:03+01:00")
                    .setFollowUpDate("2025-06-23T01:02:03+01:00")
                    .setPriority(20)
                    .build())
            .build();

    final CompleteJobRequest request =
        CompleteJobRequest.newBuilder().setJobKey(stub.getKey()).setResult(jobResult).build();

    // when
    final CompleteJobResponse response = client.completeJob(request);

    // then
    assertThat(response).isNotNull();

    final BrokerCompleteJobRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getKey());

    final JobRecord brokerRequestValue = brokerRequest.getRequestWriter();

    final List<String> expectedCorrectedAttributes =
        List.of(
            UserTaskRecord.ASSIGNEE,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE,
            UserTaskRecord.PRIORITY);

    final JobResultCorrections expectedCorrections =
        new JobResultCorrections()
            .setAssignee("Assignee")
            .setDueDate("2025-05-23T01:02:03+01:00")
            .setFollowUpDate("2025-06-23T01:02:03+01:00")
            .setCandidateUsersList(List.of())
            .setCandidateGroupsList(List.of())
            .setPriority(20);

    verifyJobResultCorrections(
        expectedCorrections, expectedCorrectedAttributes, brokerRequestValue);
  }

  private static void verifyJobResultCorrections(
      final JobResultCorrections expectedCorrections,
      final List<String> correctedAttributes,
      final JobRecord brokerRequestValue) {

    assertThat(brokerRequestValue.getResult().getCorrections()).isEqualTo(expectedCorrections);
    assertThat(brokerRequestValue.getResult().getCorrectedAttributes())
        .isEqualTo(correctedAttributes);
  }
}
