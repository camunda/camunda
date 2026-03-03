/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.job.JobActivationResponse;
import io.camunda.zeebe.gateway.protocol.rest.ActivatedJobResult;
import io.camunda.zeebe.gateway.protocol.rest.EvaluateDecisionResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskProperties;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ResponseMapperTest {

  @Nested
  class ActivatedJobMappingTest {

    static Stream<ActivatedJobWithUserTaskPropsCase> activatedJobWithUserTaskPropsCases() {
      return Stream.of(
          new ActivatedJobWithUserTaskPropsCase(
              "TASK_LISTENER job with valid user task properties in headers",
              JobKind.TASK_LISTENER,
              Map.of(
                  Protocol.USER_TASK_ACTION_HEADER_NAME, "complete",
                  Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "john",
                  Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"group1\",\"group2\"]",
                  Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user1\"]",
                  Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"assignee\"]",
                  Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2024-07-01",
                  Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "2024-07-02",
                  Protocol.USER_TASK_FORM_KEY_HEADER_NAME, "1",
                  Protocol.USER_TASK_PRIORITY_HEADER_NAME, "10",
                  Protocol.USER_TASK_KEY_HEADER_NAME, "100"),
              props -> {
                // Verify all user task properties are correctly mapped
                assertThat(props.getAction()).isEqualTo("complete");
                assertThat(props.getAssignee()).isEqualTo("john");
                assertThat(props.getCandidateGroups()).containsExactly("group1", "group2");
                assertThat(props.getCandidateUsers()).containsExactly("user1");
                assertThat(props.getChangedAttributes()).containsExactly("assignee");
                assertThat(props.getDueDate()).isEqualTo("2024-07-01");
                assertThat(props.getFollowUpDate()).isEqualTo("2024-07-02");
                assertThat(props.getFormKey()).isEqualTo("1");
                assertThat(props.getPriority()).isEqualTo(10);
                assertThat(props.getUserTaskKey()).isEqualTo("100");
              }),
          new ActivatedJobWithUserTaskPropsCase(
              "TASK_LISTENER job with invalid or empty header values",
              JobKind.TASK_LISTENER,
              Map.of(
                  Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "",
                  Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "invalid_string",
                  Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "132",
                  Protocol.USER_TASK_PRIORITY_HEADER_NAME, "<not_a_number>"),
              props -> {
                // Verify invalid or empty headers result in empty or null properties
                assertThat(props.getAction()).as("Action should be null").isNull();
                assertThat(props.getAssignee()).as("Assignee should be null").isNull();
                assertThat(props.getCandidateGroups())
                    .as("Candidate groups should be empty for invalid input")
                    .isEmpty();
                assertThat(props.getCandidateUsers())
                    .as("Candidate users should be empty for invalid input")
                    .isEmpty();
                assertThat(props.getChangedAttributes())
                    .as("Changed attributes should be empty for invalid input")
                    .isEmpty();
                assertThat(props.getDueDate()).as("Due date should be null").isNull();
                assertThat(props.getFollowUpDate()).as("Follow-up date should be null").isNull();
                assertThat(props.getFormKey()).as("Form key should be null").isNull();
                assertThat(props.getPriority()).as("Priority should be null").isNull();
                assertThat(props.getUserTaskKey()).as("User task key should be null").isNull();
              }),
          new ActivatedJobWithUserTaskPropsCase(
              "TASK_LISTENER job with empty headers map",
              JobKind.TASK_LISTENER,
              Collections.emptyMap(),
              props ->
                  assertThat(props)
                      .as(
                          "User task properties should be null for TASK_LISTENER jobs with no headers")
                      .isNull()),
          new ActivatedJobWithUserTaskPropsCase(
              "BPMN_ELEMENT with no user task properties",
              JobKind.BPMN_ELEMENT,
              Collections.singletonMap("someHeader", "someValue"),
              props ->
                  assertThat(props)
                      .as("User task properties should be null for BPMN_ELEMENT jobs")
                      .isNull()),
          new ActivatedJobWithUserTaskPropsCase(
              "EXECUTION_LISTENER with no user task properties",
              JobKind.EXECUTION_LISTENER,
              Collections.emptyMap(),
              props ->
                  assertThat(props)
                      .as("User task properties should be null for EXECUTION_LISTENER jobs")
                      .isNull()));
    }

    @ParameterizedTest
    @MethodSource("activatedJobWithUserTaskPropsCases")
    void shouldMapActivatedJobWithUserTaskPropertiesBasedOnJobKind(
        final ActivatedJobWithUserTaskPropsCase testCase) {
      // given
      final JobRecord jobRecord = mockJobRecord(testCase);

      final JobBatchRecord batchRecordMock = mock(JobBatchRecord.class);
      final ValueArray<JobRecord> jobsValueArrayMock = mockValueArray(jobRecord);
      when(batchRecordMock.jobs()).thenReturn(jobsValueArrayMock);
      final LongValue jobKey = mock(LongValue.class);
      when(jobKey.getValue()).thenReturn(123L);
      final ValueArray<LongValue> jobKeysValueArrayMock = mockValueArray(jobKey);
      when(batchRecordMock.jobKeys()).thenReturn(jobKeysValueArrayMock);
      final JobActivationResponse activationResponse =
          new JobActivationResponse(123L, batchRecordMock, 10L);

      // when
      final var result = ResponseMapper.toActivateJobsResponse(activationResponse);

      // then
      final var jobs = result.getActivateJobsResponse().getJobs();
      assertThat(jobs)
          .singleElement()
          .extracting(ActivatedJobResult::getUserTask)
          .satisfies(testCase.assertions);
    }

    private static JobRecord mockJobRecord(final ActivatedJobWithUserTaskPropsCase testCase) {
      final JobRecord jobRecord = mock(JobRecord.class);
      when(jobRecord.getJobKind()).thenReturn(testCase.jobKind);
      when(jobRecord.getCustomHeaders()).thenReturn(testCase.headers);
      when(jobRecord.getType()).thenReturn("type");
      when(jobRecord.getBpmnProcessId()).thenReturn("procId");
      when(jobRecord.getElementId()).thenReturn("elementId");
      when(jobRecord.getProcessInstanceKey()).thenReturn(1L);
      when(jobRecord.getProcessDefinitionVersion()).thenReturn(1);
      when(jobRecord.getProcessDefinitionKey()).thenReturn(2L);
      when(jobRecord.getElementInstanceKey()).thenReturn(3L);
      when(jobRecord.getWorkerBuffer()).thenReturn(BufferUtil.wrapString("worker"));
      when(jobRecord.getRetries()).thenReturn(3);
      when(jobRecord.getDeadline()).thenReturn(0L);
      when(jobRecord.getVariables()).thenReturn(Map.of());
      when(jobRecord.getTenantId()).thenReturn(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      when(jobRecord.getLength()).thenReturn(1);
      return jobRecord;
    }

    private static <T> ValueArray<T> mockValueArray(final T... values) {
      final ValueArray<T> valueArrayMock = mock(ValueArray.class);
      when(valueArrayMock.iterator()).thenReturn(List.of(values).iterator());
      return valueArrayMock;
    }

    private record ActivatedJobWithUserTaskPropsCase(
        String name,
        JobKind jobKind,
        Map<String, String> headers,
        Consumer<UserTaskProperties> assertions) {

      @Override
      public String toString() {
        return name;
      }
    }
  }

  @Nested
  class EvaluateDecisionResponseMappingTest {

    private static final DirectBuffer MSGPACK_NIL = new UnsafeBuffer(MsgPackHelper.NIL);

    @Test
    void shouldMapDecisionDefinitionTypeInEvaluatedDecisions() {
      // given
      final var record =
          new DecisionEvaluationRecord()
              .setDecisionId("decisionId")
              .setDecisionKey(100L)
              .setDecisionName("decisionName")
              .setDecisionVersion(1)
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(200L)
              .setTenantId("tenant-1");

      final var evaluatedDecision = record.evaluatedDecisions().add();
      evaluatedDecision
          .setDecisionId("innerDecisionId")
          .setDecisionKey(300L)
          .setDecisionName("innerDecisionName")
          .setDecisionVersion(2)
          .setDecisionType("DECISION_TABLE")
          .setDecisionOutput(MSGPACK_NIL)
          .setTenantId("tenant-1");

      final var brokerResponse = new BrokerResponse<>(record, 1, 999);

      // when
      final var response = ResponseMapper.toEvaluateDecisionResponse(brokerResponse);

      // then
      assertThat(response.getBody()).isNotNull();
      assertThat(((EvaluateDecisionResult) response.getBody()).getEvaluatedDecisions())
          .singleElement()
          .satisfies(
              evaluated -> {
                assertThat(evaluated.getDecisionDefinitionId()).isEqualTo("innerDecisionId");
                assertThat(evaluated.getDecisionDefinitionKey()).isEqualTo("300");
                assertThat(evaluated.getDecisionDefinitionName()).isEqualTo("innerDecisionName");
                assertThat(evaluated.getDecisionDefinitionVersion()).isEqualTo(2);
                assertThat(evaluated.getDecisionDefinitionType()).isEqualTo("DECISION_TABLE");
                assertThat(evaluated.getTenantId()).isEqualTo("tenant-1");
              });
    }

    @Test
    void shouldMapMultipleEvaluatedDecisionsWithDifferentTypes() {
      // given
      final var record =
          new DecisionEvaluationRecord()
              .setDecisionId("rootDecisionId")
              .setDecisionKey(100L)
              .setDecisionName("rootDecision")
              .setDecisionVersion(1)
              .setDecisionRequirementsId("drgId")
              .setDecisionRequirementsKey(200L)
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      final var tableDecision = record.evaluatedDecisions().add();
      tableDecision
          .setDecisionId("tableDecisionId")
          .setDecisionKey(301L)
          .setDecisionName("tableDecision")
          .setDecisionVersion(1)
          .setDecisionType("DECISION_TABLE")
          .setDecisionOutput(MSGPACK_NIL)
          .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      final var literalDecision = record.evaluatedDecisions().add();
      literalDecision
          .setDecisionId("literalDecisionId")
          .setDecisionKey(302L)
          .setDecisionName("literalDecision")
          .setDecisionVersion(1)
          .setDecisionType("DECISION_LITERAL_EXPRESSION")
          .setDecisionOutput(MSGPACK_NIL)
          .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      final var brokerResponse = new BrokerResponse<>(record, 1, 999);

      // when
      final var response = ResponseMapper.toEvaluateDecisionResponse(brokerResponse);

      // then
      assertThat(response.getBody()).isNotNull();
      assertThat(((EvaluateDecisionResult) response.getBody()).getEvaluatedDecisions())
          .hasSize(2)
          .satisfiesExactlyInAnyOrder(
              evaluated -> {
                assertThat(evaluated.getDecisionDefinitionType()).isEqualTo("DECISION_TABLE");
              },
              evaluated -> {
                assertThat(evaluated.getDecisionDefinitionType())
                    .isEqualTo("DECISION_LITERAL_EXPRESSION");
              });
    }

    @Test
    void shouldMapTopLevelFieldsInEvaluateDecisionResponse() {
      // given
      final var record =
          new DecisionEvaluationRecord()
              .setDecisionId("myDecisionId")
              .setDecisionKey(100L)
              .setDecisionName("myDecision")
              .setDecisionVersion(3)
              .setDecisionRequirementsId("myDrgId")
              .setDecisionRequirementsKey(200L)
              .setTenantId("my-tenant");

      final var brokerResponse = new BrokerResponse<>(record, 1, 555);

      // when
      final var response = ResponseMapper.toEvaluateDecisionResponse(brokerResponse);

      // then
      assertThat(response.getBody()).isNotNull();
      assertThat(((EvaluateDecisionResult) response.getBody()))
          .satisfies(
              result -> {
                assertThat(result.getDecisionDefinitionId()).isEqualTo("myDecisionId");
                assertThat(result.getDecisionDefinitionKey()).isEqualTo("100");
                assertThat(result.getDecisionDefinitionName()).isEqualTo("myDecision");
                assertThat(result.getDecisionDefinitionVersion()).isEqualTo(3);
                assertThat(result.getDecisionRequirementsId()).isEqualTo("myDrgId");
                assertThat(result.getDecisionRequirementsKey()).isEqualTo("200");
                assertThat(result.getTenantId()).isEqualTo("my-tenant");
                assertThat(result.getDecisionInstanceKey()).isEqualTo("555");
                assertThat(result.getDecisionEvaluationKey()).isEqualTo("555");
                assertThat(result.getEvaluatedDecisions()).isEmpty();
              });
    }
  }
}
