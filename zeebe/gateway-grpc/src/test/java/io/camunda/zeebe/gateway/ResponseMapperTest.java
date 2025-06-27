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
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.Protocol;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ResponseMapperTest {

  static Stream<TestCase> testCasesProvider() {
    return Stream.of(
        new TestCase(
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
                Protocol.USER_TASK_FORM_KEY_HEADER_NAME, "formKey",
                Protocol.USER_TASK_PRIORITY_HEADER_NAME, "10",
                Protocol.USER_TASK_KEY_HEADER_NAME, "utKey"),
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
                        assertThat(props.getFormKey()).isEqualTo("formKey");
                        assertThat(props.getPriority()).isEqualTo(10);
                        assertThat(props.getUserTaskKey()).isEqualTo("utKey");
                      });
            }),
        new TestCase(
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
                        assertThat(props.hasAssignee()).as("Assignee should not be set").isFalse();
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
                        assertThat(props.hasPriority()).as("Priority should not be set").isFalse();
                        assertThat(props.hasUserTaskKey())
                            .as("User task key should not be set")
                            .isFalse();
                      });
            }),
        new TestCase(
            "TASK_LISTENER job with no headers",
            JobKind.TASK_LISTENER,
            Collections.emptyMap(),
            job ->
                assertThat(job.hasUserTask())
                    .as(
                        "User task properties should not be set for TASK_LISTENER job with no headers")
                    .isFalse()),
        new TestCase(
            "BPMN_ELEMENT with no user task properties",
            JobKind.BPMN_ELEMENT,
            Collections.singletonMap("someHeader", "someValue"),
            job ->
                assertThat(job.hasUserTask())
                    .as("User task properties should not be set for BPMN_ELEMENT job")
                    .isFalse()),
        new TestCase(
            "EXECUTION_LISTENER with no user task properties",
            JobKind.EXECUTION_LISTENER,
            Collections.emptyMap(),
            job ->
                assertThat(job.hasUserTask())
                    .as("User task properties should not be set for EXECUTION_LISTENER job")
                    .isFalse()));
  }

  @ParameterizedTest
  @MethodSource("testCasesProvider")
  void shouldMapActivatedJobWithUserTaskPropertiesBasedOnJobKind(final TestCase testCase) {
    // given
    final JobRecord jobRecord = mockJobRecord(testCase);

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

    // when
    final var result = ResponseMapper.toActivatedJob(activatedJob);

    // then
    assertThat(result).satisfies(testCase.assertions);
  }

  private static JobRecord mockJobRecord(final TestCase testCase) {
    final JobRecord jobRecord = mock(JobRecord.class);
    when(jobRecord.getJobKind()).thenReturn(testCase.jobKind);
    when(jobRecord.getCustomHeaders()).thenReturn(testCase.headers);
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

  private static <T> ValueArray<T> mockValueArray(final T... values) {
    final ValueArray<T> valueArrayMock = mock(ValueArray.class);
    when(valueArrayMock.iterator()).thenReturn(List.of(values).iterator());
    return valueArrayMock;
  }

  private record TestCase(
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
