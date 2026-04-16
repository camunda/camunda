/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.ActivatedJobResult;
import io.camunda.gateway.protocol.model.UserTaskProperties;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.job.JobActivationResponse;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Collections;
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

    @Test
    void shouldMapActivatedJobWithRootProcessInstanceKey() {
      // given
      final long rootProcessInstanceKey = 789L;
      final JobRecord jobRecord =
          new JobRecord()
              .setJobKind(JobKind.BPMN_ELEMENT)
              .setType("test-type")
              .setBpmnProcessId("procId")
              .setElementId("elementId")
              .setProcessInstanceKey(456L)
              .setProcessDefinitionVersion(1)
              .setProcessDefinitionKey(123L)
              .setElementInstanceKey(555L)
              .setWorker("worker")
              .setRetries(3)
              .setDeadline(0L)
              .setRootProcessInstanceKey(rootProcessInstanceKey)
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      final byte[] emptyVariables = MsgPackConverter.convertToMsgPack(Collections.emptyMap());
      jobRecord.setVariables(new UnsafeBuffer(emptyVariables));

      final JobBatchRecord batchRecord = buildJobBatchRecordWithRootProcessInstanceKey(jobRecord);
      final JobActivationResponse activationResponse =
          new JobActivationResponse(123L, batchRecord, 1024 * 1024L);

      // when
      final var result = ResponseMapper.toActivateJobsResponse(activationResponse);

      // then
      final var jobs = result.getActivateJobsResponse().getJobs();
      assertThat(jobs)
          .singleElement()
          .satisfies(
              job -> {
                assertThat(job.getProcessInstanceKey()).isEqualTo("456");
                assertThat(job.getRootProcessInstanceKey()).isEqualTo("789");
              });
    }

    @Test
    void shouldNotSetRootProcessInstanceKeyWhenNotAvailableForActivatedJob() {
      // given - old process instance hierarchy (pre-8.9) where rootProcessInstanceKey is -1
      final JobRecord jobRecord =
          new JobRecord()
              .setJobKind(JobKind.BPMN_ELEMENT)
              .setType("test-type")
              .setBpmnProcessId("procId")
              .setElementId("elementId")
              .setProcessInstanceKey(456L)
              .setProcessDefinitionVersion(1)
              .setProcessDefinitionKey(123L)
              .setElementInstanceKey(555L)
              .setWorker("worker")
              .setRetries(3)
              .setDeadline(0L)
              // rootProcessInstanceKey defaults to -1 for old hierarchies
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      final byte[] emptyVariables = MsgPackConverter.convertToMsgPack(Collections.emptyMap());
      jobRecord.setVariables(new UnsafeBuffer(emptyVariables));

      final JobBatchRecord batchRecord = buildJobBatchRecord(jobRecord);
      final JobActivationResponse activationResponse =
          new JobActivationResponse(123L, batchRecord, 1024 * 1024L);

      // when
      final var result = ResponseMapper.toActivateJobsResponse(activationResponse);

      // then
      final var jobs = result.getActivateJobsResponse().getJobs();
      assertThat(jobs)
          .singleElement()
          .satisfies(
              job -> {
                assertThat(job.getProcessInstanceKey()).isEqualTo("456");
                // rootProcessInstanceKey should be null when it's -1 (not set)
                assertThat(job.getRootProcessInstanceKey()).isNull();
              });
    }

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
              "TASK_LISTENER job with invalid/empty headers and no action",
              JobKind.TASK_LISTENER,
              Map.of(
                  Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "",
                  Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "invalid_string",
                  Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "132",
                  Protocol.USER_TASK_PRIORITY_HEADER_NAME, "<not_a_number>"),
              props ->
                  assertThat(props)
                      .as(
                          "User task properties should be null when required action header is missing")
                      .isNull()),
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
      final JobRecord jobRecord = buildJobRecord(testCase);
      final JobBatchRecord batchRecord = buildJobBatchRecord(jobRecord);

      final JobActivationResponse activationResponse =
          new JobActivationResponse(123L, batchRecord, 1024 * 1024L);

      // when
      final var result = ResponseMapper.toActivateJobsResponse(activationResponse);

      // then
      final var jobs = result.getActivateJobsResponse().getJobs();
      assertThat(jobs)
          .singleElement()
          .extracting(ActivatedJobResult::getUserTask)
          .satisfies(testCase.assertions);
    }

    private static JobRecord buildJobRecord(final ActivatedJobWithUserTaskPropsCase testCase) {
      final JobRecord jobRecord =
          new JobRecord()
              .setJobKind(testCase.jobKind)
              .setType("type")
              .setBpmnProcessId("procId")
              .setElementId("elementId")
              .setProcessInstanceKey(1L)
              .setProcessDefinitionVersion(1)
              .setProcessDefinitionKey(2L)
              .setElementInstanceKey(3L)
              .setWorker("worker")
              .setRetries(3)
              .setDeadline(0L)
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      // Set variables as MsgPack-encoded empty map
      final byte[] emptyVariables = MsgPackConverter.convertToMsgPack(Collections.emptyMap());
      jobRecord.setVariables(new UnsafeBuffer(emptyVariables));

      // Set custom headers as msgpack-encoded buffer
      if (!testCase.headers.isEmpty()) {
        final byte[] msgpackHeaders = MsgPackConverter.convertToMsgPack(testCase.headers);
        jobRecord.setCustomHeaders(new UnsafeBuffer(msgpackHeaders));
      }

      return jobRecord;
    }

    private static JobBatchRecord buildJobBatchRecord(final JobRecord jobRecord) {
      final JobBatchRecord batchRecord = new JobBatchRecord();

      // Set required properties on the batch record
      batchRecord.setType(jobRecord.getType());
      batchRecord.setWorker(jobRecord.getWorker());

      // Add a job key
      batchRecord.jobKeys().add().setValue(123L);

      // Add the job record to the batch by copying all properties
      final JobRecord job = batchRecord.jobs().add();
      job.setJobKind(jobRecord.getJobKind())
          .setType(jobRecord.getType())
          .setBpmnProcessId(jobRecord.getBpmnProcessId())
          .setElementId(jobRecord.getElementId())
          .setProcessInstanceKey(jobRecord.getProcessInstanceKey())
          .setProcessDefinitionVersion(jobRecord.getProcessDefinitionVersion())
          .setProcessDefinitionKey(jobRecord.getProcessDefinitionKey())
          .setElementInstanceKey(jobRecord.getElementInstanceKey())
          .setWorker(jobRecord.getWorker())
          .setRetries(jobRecord.getRetries())
          .setDeadline(jobRecord.getDeadline())
          .setTenantId(jobRecord.getTenantId());

      // Set variables as empty MsgPack map
      final byte[] emptyVariables = MsgPackConverter.convertToMsgPack(Collections.emptyMap());
      job.setVariables(new UnsafeBuffer(emptyVariables));

      // Copy custom headers if present
      if (jobRecord.getCustomHeadersBuffer().capacity() > 0) {
        final byte[] headersCopy = new byte[jobRecord.getCustomHeadersBuffer().capacity()];
        jobRecord.getCustomHeadersBuffer().getBytes(0, headersCopy);
        job.setCustomHeaders(new UnsafeBuffer(headersCopy));
      }

      return batchRecord;
    }

    private static JobBatchRecord buildJobBatchRecordWithRootProcessInstanceKey(
        final JobRecord jobRecord) {
      final JobBatchRecord batchRecord = new JobBatchRecord();

      // Set required properties on the batch record
      batchRecord.setType(jobRecord.getType());
      batchRecord.setWorker(jobRecord.getWorker());

      // Add a job key
      batchRecord.jobKeys().add().setValue(123L);

      // Add the job record to the batch by copying all properties
      final JobRecord job = batchRecord.jobs().add();
      job.setJobKind(jobRecord.getJobKind())
          .setType(jobRecord.getType())
          .setBpmnProcessId(jobRecord.getBpmnProcessId())
          .setElementId(jobRecord.getElementId())
          .setProcessInstanceKey(jobRecord.getProcessInstanceKey())
          .setProcessDefinitionVersion(jobRecord.getProcessDefinitionVersion())
          .setProcessDefinitionKey(jobRecord.getProcessDefinitionKey())
          .setElementInstanceKey(jobRecord.getElementInstanceKey())
          .setWorker(jobRecord.getWorker())
          .setRetries(jobRecord.getRetries())
          .setDeadline(jobRecord.getDeadline())
          .setTenantId(jobRecord.getTenantId())
          .setRootProcessInstanceKey(jobRecord.getRootProcessInstanceKey());

      // Set variables as empty MsgPack map
      final byte[] emptyVariables = MsgPackConverter.convertToMsgPack(Collections.emptyMap());
      job.setVariables(new UnsafeBuffer(emptyVariables));

      return batchRecord;
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
      final var result = ResponseMapper.toEvaluateDecisionResponse(brokerResponse);

      // then
      assertThat(result.getEvaluatedDecisions())
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
      final var result = ResponseMapper.toEvaluateDecisionResponse(brokerResponse);

      // then
      assertThat(result.getEvaluatedDecisions()).hasSize(2);
      assertThat(result.getEvaluatedDecisions().get(0).getDecisionDefinitionType())
          .isEqualTo("DECISION_TABLE");
      assertThat(result.getEvaluatedDecisions().get(1).getDecisionDefinitionType())
          .isEqualTo("DECISION_LITERAL_EXPRESSION");
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
      final var result = ResponseMapper.toEvaluateDecisionResponse(brokerResponse);

      // then
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
    }
  }

  @Nested
  class DeleteResourceResponseMappingTest {

    @Test
    void shouldMapDeleteResourceResponseWithResourceKeyAndNoBatchOperation() {
      // given
      final long resourceKey = 12345L;
      final var brokerResponse =
          new ResourceDeletionRecord().setResourceKey(resourceKey).setDeleteHistory(false);

      // when
      final var response = ResponseMapper.toDeleteResourceResponse(brokerResponse);

      // then
      assertThat(response.getResourceKey()).isEqualTo(String.valueOf(resourceKey));
      assertThat(response.getBatchOperation()).isNull();
    }

    @Test
    void shouldMapDeleteResourceResponseWithResourceKeyAndBatchOperation() {
      // given
      final long resourceKey = 12345L;
      final long batchOperationKey = 67890L;
      final BatchOperationType batchOperationType = BatchOperationType.DELETE_PROCESS_INSTANCE;
      final var brokerResponse =
          new ResourceDeletionRecord()
              .setResourceKey(resourceKey)
              .setDeleteHistory(true)
              .setBatchOperationKey(batchOperationKey)
              .setBatchOperationType(batchOperationType);

      // when
      final var response = ResponseMapper.toDeleteResourceResponse(brokerResponse);

      // then
      assertThat(response.getResourceKey()).isEqualTo(String.valueOf(resourceKey));
      assertThat(response.getBatchOperation()).isNotNull();
      assertThat(response.getBatchOperation().getBatchOperationKey())
          .isEqualTo(String.valueOf(batchOperationKey));
      assertThat(response.getBatchOperation().getBatchOperationType()).isNotNull();
      assertThat(response.getBatchOperation().getBatchOperationType().getValue())
          .isEqualTo(batchOperationType.name());
    }
  }
}
