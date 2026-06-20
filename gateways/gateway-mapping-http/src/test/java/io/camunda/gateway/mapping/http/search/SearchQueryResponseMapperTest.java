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
import io.camunda.gateway.protocol.model.ConditionWaitStateDetails;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.camunda.gateway.protocol.model.JobListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.JobSearchQueryResult;
import io.camunda.gateway.protocol.model.JobWaitStateDetails;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentMetadata;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentReference;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Metrics;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
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
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.entities.WaitStateConditionDetails;
import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.entities.WaitStateJobDetails;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.user.CamundaUserDTO;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
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
  void shouldHandleNullOptionalFieldsInBatchOperationItemEntity() {
    // given — all optional fields null, required fields populated per OpenAPI contract
    final BatchOperationItemEntity item =
        new BatchOperationItemEntity(
            "batch-1",
            BatchOperationType.CANCEL_PROCESS_INSTANCE,
            123L,
            456L,
            null, // rootProcessInstanceKey is optional
            BatchOperationItemState.ACTIVE,
            null, // processedDate is optional
            null); // errorMessage is optional

    // when
    final BatchOperationItemResponse response =
        SearchQueryResponseMapper.toBatchOperationItem(item);

    // then — optional fields remain null
    assertThat(response.getRootProcessInstanceKey()).isNull();
    assertThat(response.getProcessedDate()).isNull();
    assertThat(response.getErrorMessage()).isNull();
    // required fields carry through
    assertThat(response.getBatchOperationKey()).isEqualTo("batch-1");
    assertThat(response.getItemKey()).isEqualTo("123");
    assertThat(response.getProcessInstanceKey()).isEqualTo("456");
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
            null, // treePath
            null); // businessId

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
    final var response = SearchQueryResponseMapper.toIncident(entity);

    // then
    assertThat(response.getState()).isEqualTo(IncidentStateEnum.UNKNOWN);
  }

  @Test
  void shouldHandleNullIncidentErrorType() {
    // given
    final var entity =
        new IncidentEntity(
            123L, // incidentKey
            456L, // processDefinitionKey
            "processId", // processDefinitionId
            789L, // processInstanceKey
            999L, // rootProcessInstanceKey
            null, // errorType
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
    assertThat(response.getErrorType()).isEqualTo(IncidentErrorTypeEnum.UNKNOWN);
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
            "processDefinitionId", // processDefinitionId
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
            .creationTime(OffsetDateTime.now())
            .lastUpdateTime(OffsetDateTime.now())
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
            .worker("test-worker")
            .state(JobState.CREATED)
            .kind(JobKind.BPMN_ELEMENT)
            .listenerEventType(ListenerEventType.UNSPECIFIED)
            .retries(1)
            .hasFailedWithRetriesLeft(false)
            .processDefinitionId("processId")
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .elementId("serviceTask1")
            .elementInstanceKey(111L)
            .rootProcessInstanceKey(null)
            .tenantId("tenant")
            .creationTime(OffsetDateTime.now())
            .lastUpdateTime(OffsetDateTime.now())
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
            .processDefinitionId("processId")
            .flowNodeId("flowNode")
            .messageName("testMessage")
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .messageSubscriptionState(MessageSubscriptionState.CREATED)
            .dateTime(OffsetDateTime.now())
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
            .correlationTime(OffsetDateTime.now())
            .flowNodeId("flowNode")
            .messageKey(222L)
            .messageName("testMessage")
            .partitionId(1)
            .processDefinitionId("processId")
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .rootProcessInstanceKey(null)
            .subscriptionKey(333L)
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
  void shouldMapBusinessIdForCorrelatedMessageSubscription() {
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
            .businessId("order-12345")
            .build();

    // when
    final var subscriptions =
        SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<CorrelatedMessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.getItems().getFirst().getBusinessId()).isEqualTo("order-12345");
  }

  @Test
  void shouldMapNullBusinessIdForCorrelatedMessageSubscription() {
    // given
    final var entity =
        CorrelatedMessageSubscriptionEntity.builder()
            .correlationKey("corrKey")
            .correlationTime(OffsetDateTime.now())
            .flowNodeId("flowNode")
            .messageKey(222L)
            .messageName("testMessage")
            .partitionId(1)
            .processDefinitionId("processId")
            .processDefinitionKey(456L)
            .processInstanceKey(789L)
            .subscriptionKey(333L)
            .tenantId("tenant")
            .businessId(null)
            .build();

    // when
    final var subscriptions =
        SearchQueryResponseMapper.toCorrelatedMessageSubscriptionSearchQueryResponse(
            new SearchQueryResult<CorrelatedMessageSubscriptionEntity>(
                1, false, List.of(entity), null, null));

    // then
    assertThat(subscriptions.getItems().getFirst().getBusinessId()).isNull();
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
            .result(AuditLogOperationResult.SUCCESS)
            .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
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
  void shouldMapRequestSourceFieldsForAuditLog() {
    // given
    final var entity =
        new AuditLogEntity.Builder()
            .auditLogKey("audit-123")
            .entityKey("entity-456")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CREATE)
            .result(AuditLogOperationResult.SUCCESS)
            .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
            .timestamp(OffsetDateTime.now())
            .inboundChannelType("MCP")
            .inboundChannelToolName("myTool")
            .build();

    // when
    final var response = SearchQueryResponseMapper.toAuditLog(entity);

    // then
    assertThat(response.getInboundChannelType()).isEqualTo("MCP");
    assertThat(response.getInboundChannelToolName()).isEqualTo("myTool");
  }

  @Test
  void shouldMapNullRequestSourceFieldsForAuditLog() {
    // given
    final var entity =
        new AuditLogEntity.Builder()
            .auditLogKey("audit-123")
            .entityKey("entity-456")
            .entityType(AuditLogEntityType.PROCESS_INSTANCE)
            .operationType(AuditLogOperationType.CREATE)
            .result(AuditLogOperationResult.SUCCESS)
            .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
            .timestamp(OffsetDateTime.now())
            .inboundChannelType(null)
            .inboundChannelToolName(null)
            .build();

    // when
    final var response = SearchQueryResponseMapper.toAuditLog(entity);

    // then
    assertThat(response.getInboundChannelType()).isNull();
    assertThat(response.getInboundChannelToolName()).isNull();
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
  void shouldMapRootProcessInstanceKeyForAgentInstance() {
    // given
    final var entity =
        new AgentInstanceEntity(
            123L, // agentInstanceKey
            List.of(456L), // elementInstanceKeys
            AgentInstanceEntity.AgentInstanceStatus.IDLE,
            new AgentInstanceEntity.AgentInstanceDefinition("gpt-4o", "openai", "You are helpful"),
            new AgentInstanceEntity.AgentInstanceMetrics(10L, 20L, 1, 2),
            new AgentInstanceEntity.AgentInstanceLimits(1000L, 5, 6),
            List.of(
                new AgentInstanceEntity.AgentInstanceTool("search", "Web search", "searchTask")),
            "agentElement", // elementId
            789L, // processInstanceKey
            999L, // rootProcessInstanceKey
            321L, // processDefinitionKey
            "processId", // processDefinitionId
            1, // processDefinitionVersion
            "v1", // versionTag
            "tenant", // tenantId
            OffsetDateTime.now(), // creationDate
            OffsetDateTime.now(), // lastUpdatedDate
            null); // completionDate

    // when
    final var response = SearchQueryResponseMapper.toAgentInstanceResult(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isEqualTo("999");
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
            "processDefinitionId", // processDefinitionId
            "tenant"); // tenantId

    // when
    final var response = SearchQueryResponseMapper.toVariableItem(entity);

    // then
    assertThat(response.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldPassThroughC8Links() {
    // given
    final var dto =
        new CamundaUserDTO(
            "displayName",
            "username",
            "email",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            Map.of(
                "admin", "https://identity.example.com",
                "operate", "https://operate.example.com",
                "tasklist", "https://tasklist.example.com"),
            true);

    // when
    final var result = SearchQueryResponseMapper.toCamundaUser(dto, List.of());

    // then
    assertThat(result.getC8Links())
        .containsEntry("admin", "https://identity.example.com")
        .containsEntry("operate", "https://operate.example.com")
        .containsEntry("tasklist", "https://tasklist.example.com");
  }

  @Test
  void shouldEnrichTenantsFromControllerProvidedList() {
    // given
    final var dto =
        new CamundaUserDTO(
            "displayName",
            "username",
            "email",
            List.of(),
            List.of("tenant-1"),
            List.of(),
            List.of(),
            null,
            Map.of(),
            true);

    final var tenant = new TenantEntity(1L, "tenant-1", "Tenant One", "desc");

    // when
    final var result = SearchQueryResponseMapper.toCamundaUser(dto, List.of(tenant));

    // then
    assertThat(result.getTenants()).hasSize(1);
    assertThat(result.getTenants().get(0).getTenantId()).isEqualTo("tenant-1");
    assertThat(result.getTenants().get(0).getName()).isEqualTo("Tenant One");
  }

  @Test
  void shouldMapJobSearchResultWithBeforeAllListenerEventType() {
    // given
    final JobEntity job =
        new JobEntity.Builder()
            .jobKey(1L)
            .type("before-all-job")
            .worker("worker1")
            .state(JobState.CREATED)
            .kind(JobKind.EXECUTION_LISTENER)
            .listenerEventType(ListenerEventType.BEFORE_ALL)
            .retries(3)
            .hasFailedWithRetriesLeft(false)
            .processDefinitionId("process1")
            .processDefinitionKey(10L)
            .processInstanceKey(20L)
            .elementId("element1")
            .elementInstanceKey(30L)
            .tenantId("default")
            .creationTime(OffsetDateTime.now())
            .lastUpdateTime(OffsetDateTime.now())
            .build();
    final SearchQueryResult<JobEntity> result =
        new SearchQueryResult<>(1, false, List.of(job), null, null);

    // when
    final JobSearchQueryResult response =
        SearchQueryResponseMapper.toJobSearchQueryResponse(result);

    // then
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().getFirst().getListenerEventType())
        .isEqualTo(JobListenerEventTypeEnum.BEFORE_ALL);
  }

  @Test
  void shouldMapAllListenerEventTypesToJobSearchResult() {
    // given / when / then
    for (final ListenerEventType type : ListenerEventType.values()) {
      final JobEntity job =
          new JobEntity.Builder()
              .jobKey(1L)
              .type("job")
              .worker("worker")
              .state(JobState.CREATED)
              .kind(JobKind.EXECUTION_LISTENER)
              .listenerEventType(type)
              .retries(1)
              .hasFailedWithRetriesLeft(false)
              .processDefinitionId("p1")
              .processDefinitionKey(1L)
              .processInstanceKey(2L)
              .elementId("e1")
              .elementInstanceKey(3L)
              .tenantId("default")
              .creationTime(OffsetDateTime.now())
              .lastUpdateTime(OffsetDateTime.now())
              .build();
      final SearchQueryResult<JobEntity> result =
          new SearchQueryResult<>(1, false, List.of(job), null, null);

      final JobSearchQueryResult response =
          SearchQueryResponseMapper.toJobSearchQueryResponse(result);

      assertThat(response.getItems().getFirst().getListenerEventType())
          .as("ListenerEventType.%s should map without error", type)
          .isNotNull()
          .isEqualTo(JobListenerEventTypeEnum.fromValue(type.name()));
    }
  }

  @Test
  void shouldMapUnspecifiedListenerEventTypeToNullInWaitStateJobDetails() {
    // given
    final var jobDetails =
        new WaitStateJobDetails(
            111L, // jobKey
            "service-task-job", // jobType
            JobKind.BPMN_ELEMENT, // jobKind
            ListenerEventType.UNSPECIFIED, // listenerEventType — sentinel for non-listener jobs
            3); // retries
    final var entity =
        new WaitStateEntity.Builder()
            .processInstanceKey(789L)
            .elementInstanceKey(111L)
            .elementId("serviceTask")
            .elementType(FlowNodeType.SERVICE_TASK)
            .rootProcessInstanceKey(999L)
            .bpmnProcessId("process1")
            .details(jobDetails)
            .tenantId("default")
            .build();

    // when
    final var result =
        SearchQueryResponseMapper.toElementInstanceWaitStateQueryResult(
            new SearchQueryResult<>(1, false, List.of(entity), null, null));

    // then
    final var responseDetails = (JobWaitStateDetails) result.getItems().getFirst().getDetails();
    assertThat(responseDetails.getListenerEventType()).isNull();
  }

  @Test
  void shouldMapConditionWaitStateDetails() {
    // given
    final var conditionDetails =
        new WaitStateConditionDetails("= x > 5", List.of("create", "update"));
    final var entity =
        new WaitStateEntity.Builder()
            .processInstanceKey(789L)
            .elementInstanceKey(111L)
            .elementId("cond-ice")
            .elementType(FlowNodeType.INTERMEDIATE_CATCH_EVENT)
            .rootProcessInstanceKey(999L)
            .bpmnProcessId("process1")
            .details(conditionDetails)
            .tenantId("default")
            .build();

    // when
    final var result =
        SearchQueryResponseMapper.toElementInstanceWaitStateQueryResult(
            new SearchQueryResult<>(1, false, List.of(entity), null, null));

    // then
    final var responseDetails =
        (ConditionWaitStateDetails) result.getItems().getFirst().getDetails();
    assertThat(responseDetails.getExpression()).isEqualTo("= x > 5");
    assertThat(responseDetails.getEvents())
        .extracting(ConditionWaitStateDetails.EventsEnum::getValue)
        .containsExactly("create", "update");
  }

  @Nested
  class AgentHistoryItemResult {

    @Test
    void shouldMapTextContentItem() {
      // given
      final var producedAt = OffsetDateTime.parse("2025-01-01T10:00:00Z");
      final var entity =
          new AgentInstanceHistoryEntity(
              100L,
              200L,
              300L,
              400L,
              500L,
              "process-1",
              "tenant-1",
              600L,
              "lease-1",
              3,
              AgentInstanceHistoryRole.USER,
              List.of(new ContentItem(ContentType.TEXT, "Hello", null, null)),
              List.of(),
              new Metrics(10, 20, 30),
              AgentInstanceHistoryCommitStatus.COMMITTED,
              producedAt);

      // when
      final var result = SearchQueryResponseMapper.toAgentHistoryItemResult(entity);

      // then
      assertThat(result.getHistoryItemKey()).isEqualTo("100");
      assertThat(result.getAgentInstanceKey()).isEqualTo("200");
      assertThat(result.getElementInstanceKey()).isEqualTo("300");
      assertThat(result.getJobKey()).isEqualTo("600");
      assertThat(result.getJobLease()).isEqualTo("lease-1");
      assertThat(result.getIteration()).isEqualTo(3);
      assertThat(result.getRole().getValue()).isEqualTo("USER");
      assertThat(result.getCommitStatus().getValue()).isEqualTo("COMMITTED");
      assertThat(result.getMetrics().getInputTokens()).isEqualTo(10);
      assertThat(result.getMetrics().getOutputTokens()).isEqualTo(20);
      assertThat(result.getMetrics().getDurationMs()).isEqualTo(30);
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).getContentType()).isEqualTo("TEXT");
    }

    @Test
    void shouldMapDocumentContentItem() {
      // given
      final var docMeta =
          new DocumentMetadata("text/pdf", "report.pdf", null, 1024L, "pd-1", 999L, Map.of());
      final var docRef = new DocumentReference("store-1", "doc-1", "hash-abc", docMeta);
      final var entity =
          new AgentInstanceHistoryEntity(
              1L,
              2L,
              3L,
              4L,
              5L,
              "pd-1",
              "<default>",
              6L,
              "",
              null,
              AgentInstanceHistoryRole.ASSISTANT,
              List.of(new ContentItem(ContentType.DOCUMENT, null, docRef, null)),
              List.of(),
              new Metrics(0, 0, 0),
              AgentInstanceHistoryCommitStatus.PENDING,
              OffsetDateTime.now());

      // when
      final var result = SearchQueryResponseMapper.toAgentHistoryItemResult(entity);

      // then
      assertThat(result.getContent()).hasSize(1);
      final var content = result.getContent().get(0);
      assertThat(content.getContentType()).isEqualTo("DOCUMENT");
      assertThat(result.getRole().getValue()).isEqualTo("ASSISTANT");
    }

    @Test
    void shouldMapNullContentAndToolCallsAsEmptyLists() {
      // given
      final var entity =
          new AgentInstanceHistoryEntity(
              1L,
              2L,
              3L,
              4L,
              5L,
              "",
              "<default>",
              6L,
              "",
              null,
              AgentInstanceHistoryRole.TOOL_RESULT,
              null,
              null,
              new Metrics(0, 0, 0),
              AgentInstanceHistoryCommitStatus.DISCARDED,
              OffsetDateTime.now());

      // when
      final var result = SearchQueryResponseMapper.toAgentHistoryItemResult(entity);

      // then
      assertThat(result.getContent()).isEmpty();
      assertThat(result.getToolCalls()).isEmpty();
    }

    @Test
    void shouldMapToolCalls() {
      // given
      final var toolCall = new ToolCall("tc-1", "MyTool", "elem-1", Map.of("param", "value"));
      final var entity =
          new AgentInstanceHistoryEntity(
              1L,
              2L,
              3L,
              4L,
              5L,
              "",
              "<default>",
              6L,
              "",
              1,
              AgentInstanceHistoryRole.ASSISTANT,
              List.of(),
              List.of(toolCall),
              new Metrics(5, 10, 100),
              AgentInstanceHistoryCommitStatus.COMMITTED,
              OffsetDateTime.now());

      // when
      final var result = SearchQueryResponseMapper.toAgentHistoryItemResult(entity);

      // then
      assertThat(result.getToolCalls()).hasSize(1);
      assertThat(result.getToolCalls().get(0).getToolCallId()).isEqualTo("tc-1");
      assertThat(result.getToolCalls().get(0).getToolName()).isEqualTo("MyTool");
      assertThat(result.getToolCalls().get(0).getElementId()).isEqualTo("elem-1");
    }

    @Test
    void shouldWrapEntityListInSearchQueryResult() {
      // given
      final var entity =
          new AgentInstanceHistoryEntity(
              42L,
              7L,
              8L,
              9L,
              10L,
              "",
              "<default>",
              11L,
              "",
              null,
              AgentInstanceHistoryRole.USER,
              List.of(),
              List.of(),
              new Metrics(0, 0, 0),
              AgentInstanceHistoryCommitStatus.COMMITTED,
              OffsetDateTime.now());
      final var queryResult = new SearchQueryResult<>(1L, false, List.of(entity), null, null);

      // when
      final var response = SearchQueryResponseMapper.toAgentHistorySearchQueryResponse(queryResult);

      // then
      assertThat(response.getItems()).hasSize(1);
      assertThat(response.getItems().get(0).getHistoryItemKey()).isEqualTo("42");
    }
  }
}
