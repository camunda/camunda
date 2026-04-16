/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryResult;
import io.camunda.gateway.protocol.model.AuditLog;
import io.camunda.gateway.protocol.model.BatchOperationItemResponse;
import io.camunda.gateway.protocol.model.BatchOperationResponse;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscription;
import io.camunda.gateway.protocol.model.DecisionInstance;
import io.camunda.gateway.protocol.model.ElementInstance;
import io.camunda.gateway.protocol.model.Incident;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.camunda.gateway.protocol.model.JobSearch;
import io.camunda.gateway.protocol.model.MessageSubscription;
import io.camunda.gateway.protocol.model.ProcessDefinition;
import io.camunda.gateway.protocol.model.ProcessInstance;
import io.camunda.gateway.protocol.model.ProcessInstanceCallHierarchyEntry;
import io.camunda.gateway.protocol.model.UserTask;
import io.camunda.gateway.protocol.model.Variable;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
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
import io.camunda.search.entities.ProcessDefinitionEntity;
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
  void shouldMapProcessDefinitionEntityToContractResult() {
    final var entity =
        new ProcessDefinitionEntity(
            2251799813685001L,
            "Demo Process", // required + nullable in contract; non-null in this case
            "demo-process",
            null,
            "demo.bpmn",
            1,
            null, // required + nullable in contract
            "tenant-a",
            null);

    final ProcessDefinition response = SearchQueryResponseMapper.toProcessDefinition(entity);

    assertThat(response.getProcessDefinitionKey()).isEqualTo("2251799813685001");
    assertThat(response.getName()).isEqualTo("Demo Process");
    assertThat(response.getResourceName()).isEqualTo("demo.bpmn");
    assertThat(response.getVersion()).isEqualTo(1);
    assertThat(response.getVersionTag()).isNull();
    assertThat(response.getProcessDefinitionId()).isEqualTo("demo-process");
    assertThat(response.getTenantId()).isEqualTo("tenant-a");
    assertThat(response.getHasStartForm()).isFalse();
  }

  @Test
  void shouldFailWhenRequiredNonNullableProcessDefinitionFieldIsNull() {
    final var entity =
        new ProcessDefinitionEntity(
            2251799813685001L,
            "Demo Process",
            "demo-process",
            null,
            null, // required + non-nullable in contract
            1,
            null,
            "tenant-a",
            null);

    assertThatThrownBy(() -> SearchQueryResponseMapper.toProcessDefinition(entity))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("resourceName");
  }

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
    assertThat(response.getState()).isEqualTo("COMPLETED");
    assertThat(response.getProcessedDate()).isEqualTo("2025-01-15T11:53:00.000Z");
    assertThat(response.getErrorMessage()).isEqualTo("errorMessage");
  }

  @Test
  void shouldDefaultNullBatchOperationErrorsToEmptyList() {
    final var entity =
        new BatchOperationEntity(
            "2251799813685001",
            BatchOperationState.ACTIVE,
            BatchOperationType.MIGRATE_PROCESS_INSTANCE,
            OffsetDateTime.parse("2025-01-15T11:53:00Z"),
            null,
            null,
            null,
            10,
            1,
            9,
            null);

    final BatchOperationResponse response = SearchQueryResponseMapper.toBatchOperation(entity);

    assertThat(response.getErrors()).isNotNull().isEmpty();
  }

  @Test
  void shouldFailWhenRequiredNonNullableBatchOperationItemFieldIsNull() {
    // given — operationType is required + non-nullable in the contract
    final BatchOperationItemEntity item =
        new BatchOperationItemEntity(
            "batchOperationKey",
            null,
            1234L,
            4321L,
            null,
            BatchOperationItemState.COMPLETED,
            null,
            null);

    // when / then
    assertThatThrownBy(() -> SearchQueryResponseMapper.toBatchOperationItem(item))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("operationType");
  }

  @Test
  void shouldHandleNullOptionalFieldsInBatchOperationItemEntity() {
    // given — required fields populated, optional fields null
    final BatchOperationItemEntity item =
        new BatchOperationItemEntity(
            "batchOperationKey",
            BatchOperationType.MIGRATE_PROCESS_INSTANCE,
            1234L,
            4321L,
            null, // rootProcessInstanceKey (optional)
            BatchOperationItemState.COMPLETED,
            null, // processedDate (optional)
            null); // errorMessage (optional)

    // when
    final BatchOperationItemResponse response =
        SearchQueryResponseMapper.toBatchOperationItem(item);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
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
            null, // treePath
            null); // businessId

    // when
    final ProcessInstance response = SearchQueryResponseMapper.toProcessInstance(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldFallbackToProcessDefinitionIdForCallHierarchyWhenNameBlank() {
    final var entity =
        new ProcessInstanceEntity(
            123L,
            999L,
            "demo-process",
            "   ",
            1,
            null,
            456L,
            null,
            null,
            OffsetDateTime.now(),
            null,
            ProcessInstanceState.ACTIVE,
            false,
            "tenant",
            null,
            null);

    final ProcessInstanceCallHierarchyEntry response =
        SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntry(entity);

    assertThat(response.getProcessDefinitionName()).isEqualTo("demo-process");
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
            null, // treePath
            null); // businessId

    // when
    final ProcessInstance response = SearchQueryResponseMapper.toProcessInstance(entity);

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
    final UserTask response = SearchQueryResponseMapper.toUserTask(entity);

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
    final Incident response = SearchQueryResponseMapper.toIncident(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
  }

  @Test
  void shouldHandleNullIncidentState() {
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
            null, // state
            222L, // jobKey
            "tenant"); // tenantId

    // when
    final Incident response = SearchQueryResponseMapper.toIncident(entity);

    // then
    assertThat(response.getState()).isEqualTo(IncidentStateEnum.UNKNOWN);
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
    final ElementInstance response = SearchQueryResponseMapper.toElementInstance(entity);

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
    final DecisionInstance response = SearchQueryResponseMapper.toDecisionInstance(entity);

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
    final Variable response = SearchQueryResponseMapper.toVariableItem(entity);

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
    final StrictSearchQueryResult<JobSearch> jobs =
        SearchQueryResponseMapper.toJobSearchQueryResponse(
            new SearchQueryResult<JobEntity>(1, false, List.of(entity), null, null));

    // then
    assertThat(jobs.items().getFirst().getRootProcessInstanceKey()).isEqualTo("999");
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
            .elementInstanceKey(0L)
            .hasFailedWithRetriesLeft(false)
            .processDefinitionId("")
            .processDefinitionKey(0L)
            .retries(0)
            .worker("")
            .build();

    // when
    final StrictSearchQueryResult<JobSearch> jobs =
        SearchQueryResponseMapper.toJobSearchQueryResponse(
            new SearchQueryResult<JobEntity>(1, false, List.of(entity), null, null));

    // then
    assertThat(jobs.items().getFirst().getRootProcessInstanceKey()).isNull();
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
    final StrictSearchQueryResult<MessageSubscription> subscriptions =
        SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<MessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.items().getFirst().getRootProcessInstanceKey()).isEqualTo("999");
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
            .processDefinitionId("")
            .flowNodeId("")
            .dateTime(OffsetDateTime.now())
            .messageName("")
            .build();

    // when
    final StrictSearchQueryResult<MessageSubscription> subscriptions =
        SearchQueryResponseMapper.toMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<MessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.items().getFirst().getRootProcessInstanceKey()).isNull();
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
    final StrictSearchQueryResult<CorrelatedMessageSubscription> subscriptions =
        SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<CorrelatedMessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.items().getFirst().getRootProcessInstanceKey()).isEqualTo("999");
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
            .correlationTime(OffsetDateTime.now())
            .flowNodeId("")
            .messageKey(0L)
            .messageName("")
            .partitionId(0)
            .processDefinitionId("")
            .processDefinitionKey(0L)
            .subscriptionKey(0L)
            .build();

    // when
    final StrictSearchQueryResult<CorrelatedMessageSubscription> subscriptions =
        SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<CorrelatedMessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.items().getFirst().getRootProcessInstanceKey()).isNull();
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
    final AuditLog response = SearchQueryResponseMapper.toAuditLog(entity);

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
            .result(AuditLogOperationResult.SUCCESS)
            .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .build();

    // when
    final AuditLog response = SearchQueryResponseMapper.toAuditLog(entity);

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
    final UserTask response = SearchQueryResponseMapper.toUserTask(entity);

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
    final Incident response = SearchQueryResponseMapper.toIncident(entity);

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
    final ElementInstance response = SearchQueryResponseMapper.toElementInstance(entity);

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
    final DecisionInstance response = SearchQueryResponseMapper.toDecisionInstance(entity);

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
    final Variable response = SearchQueryResponseMapper.toVariableItem(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }
}
