/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceResponse;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
              job -> {
                assertThat(job.hasUserTask())
                    .as("User task properties should be set for TASK_LISTENER job")
                    .isTrue();
                assertThat(job.getUserTask())
                    .satisfies(
                        props -> {
                          // Verify all user task properties are correctly mapped
                          assertThat(props.getAction()).isEqualTo("complete");
                          assertThat(props.getAssignee()).isEqualTo("john");
                          assertThat(props.getCandidateGroupsList())
                              .containsExactly("group1", "group2");
                          assertThat(props.getCandidateUsersList()).containsExactly("user1");
                          assertThat(props.getChangedAttributesList()).containsExactly("assignee");
                          assertThat(props.getDueDate()).isEqualTo("2024-07-01");
                          assertThat(props.getFollowUpDate()).isEqualTo("2024-07-02");
                          assertThat(props.getFormKey()).isEqualTo(1);
                          assertThat(props.getPriority()).isEqualTo(10);
                          assertThat(props.getUserTaskKey()).isEqualTo(100);
                        });
              }),
          new ActivatedJobWithUserTaskPropsCase(
              "TASK_LISTENER job with invalid or empty header values",
              JobKind.TASK_LISTENER,
              Map.of(
                  Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "",
                  Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "invalid_string",
                  Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "132",
                  Protocol.USER_TASK_PRIORITY_HEADER_NAME, "<not_a_number>"),
              job -> {
                assertThat(job.hasUserTask())
                    .as("User task properties should be set for TASK_LISTENER job")
                    .isTrue();
                assertThat(job.getUserTask())
                    .satisfies(
                        props -> {
                          assertThat(props.hasAction()).as("Action should not be set").isFalse();
                          assertThat(props.hasAssignee())
                              .as("Assignee should not be set")
                              .isFalse();
                          assertThat(props.getCandidateGroupsList())
                              .as("Candidate groups should be empty for invalid input")
                              .isEmpty();
                          assertThat(props.getCandidateUsersList())
                              .as("Candidate users should be empty for invalid input")
                              .isEmpty();
                          assertThat(props.getChangedAttributesList())
                              .as("Changed attributes should be empty for invalid input")
                              .isEmpty();
                          assertThat(props.hasDueDate()).as("Due date should not be set").isFalse();
                          assertThat(props.hasFollowUpDate())
                              .as("Follow-up date should not be set")
                              .isFalse();
                          assertThat(props.hasFormKey()).as("Form key should not be set").isFalse();
                          assertThat(props.hasPriority())
                              .as("Priority should not be set")
                              .isFalse();
                          assertThat(props.hasUserTaskKey())
                              .as("User task key should not be set")
                              .isFalse();
                        });
              }),
          new ActivatedJobWithUserTaskPropsCase(
              "TASK_LISTENER job with no headers",
              JobKind.TASK_LISTENER,
              Collections.emptyMap(),
              job ->
                  assertThat(job.hasUserTask())
                      .as(
                          "User task properties should not be set for TASK_LISTENER job with no headers")
                      .isFalse()),
          new ActivatedJobWithUserTaskPropsCase(
              "BPMN_ELEMENT with no user task properties",
              JobKind.BPMN_ELEMENT,
              Collections.singletonMap("someHeader", "someValue"),
              job ->
                  assertThat(job.hasUserTask())
                      .as("User task properties should not be set for BPMN_ELEMENT job")
                      .isFalse()),
          new ActivatedJobWithUserTaskPropsCase(
              "EXECUTION_LISTENER with no user task properties",
              JobKind.EXECUTION_LISTENER,
              Collections.emptyMap(),
              job ->
                  assertThat(job.hasUserTask())
                      .as("User task properties should not be set for EXECUTION_LISTENER job")
                      .isFalse()));
    }

    @ParameterizedTest
    @MethodSource("activatedJobWithUserTaskPropsCases")
    void shouldMapActivatedJobWithUserTaskPropertiesBasedOnJobKind(
        final ActivatedJobWithUserTaskPropsCase testCase) {
      // given
      final JobRecord jobRecord = mockJobRecord(testCase.jobKind, testCase.headers);
      final var activatedJob = mockActivatedJob(jobRecord);

      // when
      final var result = ResponseMapper.toActivatedJob(activatedJob);

      // then
      assertThat(result).satisfies(testCase.assertions);
    }

    @ParameterizedTest
    @EnumSource(JobKind.class)
    void shouldMapActivatedJobWithJobKind(final JobKind jobKind) {
      // given
      final JobRecord jobRecord = mockJobRecord(jobKind, Map.of());
      final var activatedJob = mockActivatedJob(jobRecord);

      // when
      final var result = ResponseMapper.toActivatedJob(activatedJob);

      // then
      assertThat(result.getKind().name()).isEqualTo(jobKind.name());
    }

    @ParameterizedTest
    @EnumSource(JobListenerEventType.class)
    void shouldMapActivatedJobWithListenerEventType(final JobListenerEventType listenerEventType) {
      // given
      final JobRecord jobRecord = mockJobRecord(JobKind.TASK_LISTENER, Map.of());
      when(jobRecord.getJobListenerEventType()).thenReturn(listenerEventType);

      final var activatedJob = mockActivatedJob(jobRecord);

      // when
      final var result = ResponseMapper.toActivatedJob(activatedJob);

      // then
      assertThat(result.getListenerEventType().name()).isEqualTo(listenerEventType.name());
    }

    private static JobRecord mockJobRecord(
        final JobKind jobKind, final Map<String, String> customHeaders) {
      final JobRecord jobRecord = mock(JobRecord.class);
      when(jobRecord.getJobKind()).thenReturn(jobKind);
      when(jobRecord.getJobListenerEventType()).thenReturn(JobListenerEventType.START);
      when(jobRecord.getCustomHeaders()).thenReturn(customHeaders);
      when(jobRecord.getTypeBuffer()).thenReturn(BufferUtil.wrapString("type"));
      when(jobRecord.getBpmnProcessId()).thenReturn("procId");
      when(jobRecord.getElementId()).thenReturn("elementId");
      when(jobRecord.getProcessInstanceKey()).thenReturn(1L);
      when(jobRecord.getProcessDefinitionVersion()).thenReturn(1);
      when(jobRecord.getProcessDefinitionKey()).thenReturn(2L);
      when(jobRecord.getElementInstanceKey()).thenReturn(3L);
      when(jobRecord.getWorkerBuffer()).thenReturn(BufferUtil.wrapString("worker"));
      when(jobRecord.getCustomHeadersBuffer()).thenReturn(BufferUtil.wrapString("{}"));
      when(jobRecord.getVariablesBuffer()).thenReturn(BufferUtil.wrapString("{}"));
      when(jobRecord.getRetries()).thenReturn(3);
      when(jobRecord.getDeadline()).thenReturn(0L);
      when(jobRecord.getVariables()).thenReturn(Map.of());
      when(jobRecord.getTenantId()).thenReturn(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      when(jobRecord.getLength()).thenReturn(1);
      return jobRecord;
    }

    private io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob mockActivatedJob(
        final JobRecord jobRecord) {
      final JobBatchRecord batchRecordMock = mock(JobBatchRecord.class);
      final ValueArray<JobRecord> jobsValueArrayMock = mockValueArray(jobRecord);
      when(batchRecordMock.jobs()).thenReturn(jobsValueArrayMock);
      final LongValue jobKey = mock(LongValue.class);
      when(jobKey.getValue()).thenReturn(123L);
      final ValueArray<LongValue> jobKeysValueArrayMock = mockValueArray(jobKey);
      when(batchRecordMock.jobKeys()).thenReturn(jobKeysValueArrayMock);

      final var activatedJob = mock(io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob.class);
      when(activatedJob.jobKey()).thenReturn(123L);
      when(activatedJob.jobRecord()).thenReturn(jobRecord);

      return activatedJob;
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
        Consumer<ActivatedJob> assertions) {

      @Override
      public String toString() {
        return name;
      }
    }
  }

  @Nested
  class DeleteResourceResponseMappingTest {

    @Test
    void shouldMapDeleteResourceResponseWithResourceKeyAndNoBatchOperation() {
      // given
      final long resourceKey = 12345L;
      final ResourceDeletionRecord brokerResponse = mock(ResourceDeletionRecord.class);
      when(brokerResponse.getResourceKey()).thenReturn(resourceKey);
      when(brokerResponse.isDeleteHistory()).thenReturn(false);

      // when
      final DeleteResourceResponse result =
          ResponseMapper.toDeleteResourceResponse(1L, brokerResponse);

      // then
      assertThat(result.getResourceKey()).isEqualTo(String.valueOf(resourceKey));
      assertThat(result.hasBatchOperation()).isFalse();
    }

    @Test
    void shouldMapDeleteResourceResponseWithBatchOperationWhenDeleteHistoryIsTrue() {
      // given
      final long resourceKey = 12345L;
      final long batchOperationKey = 67890L;
      final BatchOperationType batchOperationType = BatchOperationType.DELETE_PROCESS_INSTANCE;
      final ResourceDeletionRecord brokerResponse = mock(ResourceDeletionRecord.class);
      when(brokerResponse.getResourceKey()).thenReturn(resourceKey);
      when(brokerResponse.isDeleteHistory()).thenReturn(true);
      when(brokerResponse.getBatchOperationKey()).thenReturn(batchOperationKey);
      when(brokerResponse.getBatchOperationType()).thenReturn(batchOperationType);

      // when
      final DeleteResourceResponse result =
          ResponseMapper.toDeleteResourceResponse(1L, brokerResponse);

      // then
      assertThat(result.getResourceKey()).isEqualTo(String.valueOf(resourceKey));
      assertThat(result.hasBatchOperation()).isTrue();
      assertThat(result.getBatchOperation().getBatchOperationKey())
          .isEqualTo(String.valueOf(batchOperationKey));
      assertThat(result.getBatchOperation().getBatchOperationType()).isNotNull();
      assertThat(result.getBatchOperation().getBatchOperationType().name())
          .isEqualTo(batchOperationType.name());
    }
  }
}
