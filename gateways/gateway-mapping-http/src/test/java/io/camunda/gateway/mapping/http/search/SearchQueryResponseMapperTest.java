/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.BatchOperationItemResponse;
import io.camunda.gateway.protocol.model.BatchOperationItemResponse.StateEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchQueryResponseMapperTest {

  @Test
  void shouldConvertBatchOperationItemEntity() {
    // given
    final BatchOperationItemEntity item =
        new BatchOperationItemEntity(
            "batchOperationKey",
            BatchOperationType.MIGRATE_PROCESS_INSTANCE,
            1234L,
            4321L,
            4320L,
            BatchOperationItemState.COMPLETED,
            OffsetDateTime.parse("2025-01-15T11:53:00Z"),
            "errorMessage");

    // when
    final BatchOperationItemResponse response =
        SearchQueryResponseMapper.toBatchOperationItem(item);

    // then
    assertThat(response.getBatchOperationKey()).isEqualTo("batchOperationKey");
    assertThat(response.getOperationType())
        .isEqualTo(BatchOperationTypeEnum.MIGRATE_PROCESS_INSTANCE);
    assertThat(response.getItemKey()).isEqualTo("1234");
    assertThat(response.getProcessInstanceKey()).isEqualTo("4321");
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("4320");
    assertThat(response.getState()).isEqualTo(StateEnum.COMPLETED);
    assertThat(response.getProcessedDate()).isEqualTo("2025-01-15T11:53:00.000Z");
    assertThat(response.getErrorMessage()).isEqualTo("errorMessage");
  }

  @Test
  void shouldHandleNullFieldsInBatchOperationItemEntity() {
    // given
    final BatchOperationItemEntity item =
        new BatchOperationItemEntity(null, null, null, null, null, null, null, null);

    // when
    final BatchOperationItemResponse response =
        SearchQueryResponseMapper.toBatchOperationItem(item);

    // then
    assertThat(response.getBatchOperationKey()).isNull();
    assertThat(response.getOperationType()).isNull();
    assertThat(response.getItemKey()).isNull();
    assertThat(response.getProcessInstanceKey()).isNull();
    assertThat(response.getState()).isNull();
    assertThat(response.getProcessedDate()).isNull();
    assertThat(response.getErrorMessage()).isNull();
  }

  @Test
  void shouldMapRootProcessInstanceKeyForProcessInstance() {
    // given
    final var entity =
        new ProcessInstanceEntity(
            123L, // processInstanceKey
            999L, // rootProcessInstanceKey
            "demoProcess", // processDefinitionId
            "Demo Process", // processDefinitionName
            1, // processDefinitionVersion
            null, // processDefinitionVersionTag
            456L, // processDefinitionKey
            null, // parentProcessInstanceKey
            null, // parentFlowNodeInstanceKey
            OffsetDateTime.now(), // startDate
            null, // endDate
            ProcessInstanceState.ACTIVE, // state
            false, // hasIncident
            "tenant", // tenantId
            null); // treePath

    // when
    final var response = SearchQueryResponseMapper.toProcessInstance(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForProcessInstance() {
    // given
    final var entity =
        new ProcessInstanceEntity(
            123L, // processInstanceKey
            null, // rootProcessInstanceKey
            "demoProcess", // processDefinitionId
            "Demo Process", // processDefinitionName
            1, // processDefinitionVersion
            null, // processDefinitionVersionTag
            456L, // processDefinitionKey
            null, // parentProcessInstanceKey
            null, // parentFlowNodeInstanceKey
            OffsetDateTime.now(), // startDate
            null, // endDate
            ProcessInstanceState.ACTIVE, // state
            false, // hasIncident
            "tenant", // tenantId
            null); // treePath

    // when
    final var response = SearchQueryResponseMapper.toProcessInstance(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapRootProcessInstanceKeyForUserTask() {
    // given
    final var entity =
        new UserTaskEntity(
            123L, // userTaskKey
            "userTask1", // elementId
            "User Task", // name
            "processId", // processDefinitionId
            "Process Name", // processName
            OffsetDateTime.now(), // creationDate
            null, // completionDate
            null, // assignee
            UserTaskState.CREATED, // state
            null, // formKey
            789L, // processDefinitionKey
            456L, // processInstanceKey
            999L, // rootProcessInstanceKey
            111L, // elementInstanceKey
            "tenant", // tenantId
            null, // dueDate
            null, // followUpDate
            null, // candidateGroups
            null, // candidateUsers
            null, // externalFormReference
            1, // processDefinitionVersion
            null, // customHeaders
            50, // priority
            null); // tags

    // when
    final var response = SearchQueryResponseMapper.toUserTask(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapRootProcessInstanceKeyForIncident() {
    // given
    final var entity =
        new IncidentEntity(
            123L, // incidentKey
            456L, // processDefinitionKey
            "processId", // processDefinitionId
            789L, // processInstanceKey
            999L, // rootProcessInstanceKey
            ErrorType.JOB_NO_RETRIES, // errorType
            "Error message", // errorMessage
            "flowNodeId", // flowNodeId
            111L, // flowNodeInstanceKey
            OffsetDateTime.now(), // creationTime
            IncidentState.ACTIVE, // state
            222L, // jobKey
            "tenant"); // tenantId

    // when
    final var response = SearchQueryResponseMapper.toIncident(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapRootProcessInstanceKeyForElementInstance() {
    // given
    final var entity =
        new FlowNodeInstanceEntity(
            123L, // flowNodeInstanceKey
            456L, // processInstanceKey
            999L, // rootProcessInstanceKey
            789L, // processDefinitionKey
            OffsetDateTime.now(), // startDate
            null, // endDate
            "flowNodeId", // flowNodeId
            "Flow Node Name", // flowNodeName
            null, // treePath
            FlowNodeType.SERVICE_TASK, // type
            FlowNodeState.ACTIVE, // state
            false, // hasIncident
            null, // incidentKey
            "processId", // processDefinitionId
            "tenant", // tenantId
            null); // level

    // when
    final var response = SearchQueryResponseMapper.toElementInstance(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapRootProcessInstanceKeyForDecisionInstance() {
    // given
    final var entity =
        new DecisionInstanceEntity.Builder()
            .decisionInstanceKey(123L)
            .decisionInstanceId("decision-id")
            .state(DecisionInstanceState.EVALUATED)
            .evaluationDate(OffsetDateTime.now())
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(999L)
            .flowNodeInstanceKey(111L)
            .decisionDefinitionKey(222L)
            .decisionDefinitionId("decisionId")
            .decisionDefinitionName("Decision Name")
            .decisionDefinitionVersion(1)
            .decisionDefinitionType(DecisionDefinitionType.DECISION_TABLE)
            .rootDecisionDefinitionKey(333L)
            .result("result")
            .tenantId("tenant")
            .build();

    // when
    final var response = SearchQueryResponseMapper.toDecisionInstance(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapRootProcessInstanceKeyForVariableItem() {
    // given
    final var entity =
        new VariableEntity(
            123L, // variableKey
            "varName", // name
            "value", // value
            null, // fullValue
            false, // isPreview
            456L, // scopeKey
            789L, // processInstanceKey
            999L, // rootProcessInstanceKey
            null, // processDefinitionId
            "tenant"); // tenantId

    // when
    final var response = SearchQueryResponseMapper.toVariableItem(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapRootProcessInstanceKeyForJob() {
    // given
    final var entity =
        new JobEntity.Builder()
            .jobKey(123L)
            .type("test-type")
            .worker("test-worker")
            .state(JobState.CREATED)
            .kind(JobKind.BPMN_ELEMENT)
            .listenerEventType(ListenerEventType.UNSPECIFIED)
            .retries(3)
            .isDenied(false)
            .hasFailedWithRetriesLeft(false)
            .processDefinitionId("processId")
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(999L)
            .elementId("serviceTask1")
            .elementInstanceKey(111L)
            .tenantId("tenant")
            .build();

    // when
    final var jobs =
        SearchQueryResponseMapper.toJobSearchQueryResponse(
            new SearchQueryResult<JobEntity>(1, false, List.of(entity), null, null));

    // then
    assertThat(jobs.getItems().getFirst().getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForJob() {
    // given
    final var entity =
        new JobEntity.Builder()
            .jobKey(123L)
            .type("test-type")
            .state(JobState.CREATED)
            .kind(JobKind.BPMN_ELEMENT)
            .listenerEventType(ListenerEventType.UNSPECIFIED)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .tenantId("tenant")
            .build();

    // when
    final var jobs =
        SearchQueryResponseMapper.toJobSearchQueryResponse(
            new SearchQueryResult<JobEntity>(1, false, List.of(entity), null, null));

    // then
    assertThat(jobs.getItems().getFirst().getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapRootProcessInstanceKeyForMessageSubscription() {
    // given
    final var entity =
        MessageSubscriptionEntity.builder()
            .messageSubscriptionKey(123L)
            .processDefinitionId("processId")
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(999L)
            .flowNodeId("messageCatch")
            .flowNodeInstanceKey(111L)
            .messageSubscriptionState(MessageSubscriptionState.CREATED)
            .dateTime(OffsetDateTime.now())
            .messageName("testMessage")
            .correlationKey("corrKey")
            .tenantId("tenant")
            .build();

    // when
    final var subscriptions =
        SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<MessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.getItems().getFirst().getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForMessageSubscription() {
    // given
    final var entity =
        MessageSubscriptionEntity.builder()
            .messageSubscriptionKey(123L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .messageSubscriptionState(MessageSubscriptionState.CREATED)
            .tenantId("tenant")
            .build();

    // when
    final var subscriptions =
        SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<MessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.getItems().getFirst().getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapRootProcessInstanceKeyForCorrelatedMessageSubscription() {
    // given
    final var entity =
        CorrelatedMessageSubscriptionEntity.builder()
            .correlationKey("corrKey")
            .correlationTime(OffsetDateTime.now())
            .flowNodeId("messageCatch")
            .flowNodeInstanceKey(111L)
            .messageKey(222L)
            .messageName("testMessage")
            .partitionId(1)
            .processDefinitionId("processId")
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(999L)
            .subscriptionKey(333L)
            .tenantId("tenant")
            .build();

    // when
    final var subscriptions =
        SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<CorrelatedMessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.getItems().getFirst().getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForCorrelatedMessageSubscription() {
    // given
    final var entity =
        CorrelatedMessageSubscriptionEntity.builder()
            .correlationKey("corrKey")
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .tenantId("tenant")
            .build();

    // when
    final var subscriptions =
        SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<CorrelatedMessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.getItems().getFirst().getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapRootProcessInstanceKeyForSequenceFlow() {
    // given
    final var entity =
        new SequenceFlowEntity.Builder()
            .sequenceFlowId("flow1")
            .flowNodeId("task1")
            .processInstanceKey(789L)
            .rootProcessInstanceKey(999L)
            .processDefinitionKey(456L)
            .processDefinitionId("processId")
            .tenantId("tenant")
            .build();

    // when
    final var sequenceFlows = SearchQueryResponseMapper.toSequenceFlowsResult(List.of(entity));

    // then
    assertThat(sequenceFlows.getItems().getFirst().getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapRootProcessInstanceKeyForAuditLog() {
    // given
    final var entity =
        new AuditLogEntity.Builder()
            .auditLogKey("audit-123")
            .entityKey("entity-456")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CREATE)
            .timestamp(OffsetDateTime.now())
            .actorId("user1")
            .actorType(AuditLogActorType.USER)
            .tenantId("tenant")
            .result(AuditLogOperationResult.SUCCESS)
            .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
            .processDefinitionId("processId")
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(999L)
            .build();

    // when
    final var response = SearchQueryResponseMapper.toAuditLog(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForAuditLog() {
    // given
    final var entity =
        new AuditLogEntity.Builder()
            .auditLogKey("audit-123")
            .entityKey("entity-456")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CREATE)
            .timestamp(OffsetDateTime.now())
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .build();

    // when
    final var response = SearchQueryResponseMapper.toAuditLog(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForUserTask() {
    // given
    final var entity =
        new UserTaskEntity(
            123L, // userTaskKey
            "userTask1", // elementId
            "User Task", // name
            "processId", // processDefinitionId
            "Process Name", // processName
            OffsetDateTime.now(), // creationDate
            null, // completionDate
            null, // assignee
            UserTaskState.CREATED, // state
            null, // formKey
            789L, // processDefinitionKey
            456L, // processInstanceKey
            null, // rootProcessInstanceKey
            111L, // elementInstanceKey
            "tenant", // tenantId
            null, // dueDate
            null, // followUpDate
            null, // candidateGroups
            null, // candidateUsers
            null, // externalFormReference
            1, // processDefinitionVersion
            null, // customHeaders
            50, // priority
            null); // tags

    // when
    final var response = SearchQueryResponseMapper.toUserTask(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForIncident() {
    // given
    final var entity =
        new IncidentEntity(
            123L, // incidentKey
            456L, // processDefinitionKey
            "processId", // processDefinitionId
            789L, // processInstanceKey
            null, // rootProcessInstanceKey
            ErrorType.JOB_NO_RETRIES, // errorType
            "Error message", // errorMessage
            "flowNodeId", // flowNodeId
            111L, // flowNodeInstanceKey
            OffsetDateTime.now(), // creationTime
            IncidentState.ACTIVE, // state
            222L, // jobKey
            "tenant"); // tenantId

    // when
    final var response = SearchQueryResponseMapper.toIncident(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForElementInstance() {
    // given
    final var entity =
        new FlowNodeInstanceEntity(
            123L, // flowNodeInstanceKey
            456L, // processInstanceKey
            null, // rootProcessInstanceKey
            789L, // processDefinitionKey
            OffsetDateTime.now(), // startDate
            null, // endDate
            "flowNodeId", // flowNodeId
            "Flow Node Name", // flowNodeName
            null, // treePath
            FlowNodeType.SERVICE_TASK, // type
            FlowNodeState.ACTIVE, // state
            false, // hasIncident
            null, // incidentKey
            "processId", // processDefinitionId
            "tenant", // tenantId
            null); // level

    // when
    final var response = SearchQueryResponseMapper.toElementInstance(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForDecisionInstance() {
    // given
    final var entity =
        new DecisionInstanceEntity.Builder()
            .decisionInstanceKey(123L)
            .decisionInstanceId("decision-id")
            .state(DecisionInstanceState.EVALUATED)
            .evaluationDate(OffsetDateTime.now())
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .flowNodeInstanceKey(111L)
            .decisionDefinitionKey(222L)
            .decisionDefinitionId("decisionId")
            .decisionDefinitionName("Decision Name")
            .decisionDefinitionVersion(1)
            .decisionDefinitionType(DecisionDefinitionType.DECISION_TABLE)
            .rootDecisionDefinitionKey(333L)
            .result("result")
            .tenantId("tenant")
            .build();

    // when
    final var response = SearchQueryResponseMapper.toDecisionInstance(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNullRootProcessInstanceKeyForVariableItem() {
    // given
    final var entity =
        new VariableEntity(
            123L, // variableKey
            "varName", // name
            "value", // value
            null, // fullValue
            false, // isPreview
            456L, // scopeKey
            789L, // processInstanceKey
            null, // rootProcessInstanceKey
            null, // processDefinitionId
            "tenant"); // tenantId

    // when
    final var response = SearchQueryResponseMapper.toVariableItem(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }
}
