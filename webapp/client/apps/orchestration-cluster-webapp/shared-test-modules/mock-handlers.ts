/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {endpoints} from '@camunda/camunda-api-zod-schemas/8.10';
import {createEndpointMock} from './mock-endpoint';

const mockQueryUserTasksEndpoint = createEndpointMock({
	endpoint: endpoints.queryUserTasks.getUrl(),
	method: endpoints.queryUserTasks.method,
});

const mockGetProcessDefinitionInstanceStatisticsEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessDefinitionInstanceStatistics.getUrl(),
	method: endpoints.getProcessDefinitionInstanceStatistics.method,
});

const mockQueryProcessDefinitionsEndpoint = createEndpointMock({
	endpoint: endpoints.queryProcessDefinitions.getUrl(),
	method: endpoints.queryProcessDefinitions.method,
});

const mockGetProcessDefinitionEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessDefinition.getUrl({processDefinitionKey: ':processDefinitionKey'}),
	method: endpoints.getProcessDefinition.method,
});

const mockGetProcessStartFormEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessStartForm.getUrl({processDefinitionKey: ':processDefinitionKey'}),
	method: endpoints.getProcessStartForm.method,
});

const mockCreateProcessInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.createProcessInstance.getUrl(),
	method: endpoints.createProcessInstance.method,
});

const mockCreateDocumentsEndpoint = createEndpointMock({
	endpoint: endpoints.createDocuments.getUrl(),
	method: endpoints.createDocuments.method,
});

const mockGetIncidentProcessInstanceStatisticsByErrorEndpoint = createEndpointMock({
	endpoint: endpoints.getIncidentProcessInstanceStatisticsByError.getUrl(),
	method: endpoints.getIncidentProcessInstanceStatisticsByError.method,
});

const mockGetProcessDefinitionInstanceVersionStatisticsEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessDefinitionInstanceVersionStatistics.getUrl(),
	method: endpoints.getProcessDefinitionInstanceVersionStatistics.method,
});

const mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint = createEndpointMock({
	endpoint: endpoints.getIncidentProcessInstanceStatisticsByDefinition.getUrl(),
	method: endpoints.getIncidentProcessInstanceStatisticsByDefinition.method,
});

const mockQueryBatchOperationsEndpoint = createEndpointMock({
	endpoint: endpoints.queryBatchOperations.getUrl(),
	method: endpoints.queryBatchOperations.method,
});

const mockGetBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.getBatchOperation.getUrl({batchOperationKey: ':batchOperationKey'}),
	method: endpoints.getBatchOperation.method,
});

const mockQueryBatchOperationItemsEndpoint = createEndpointMock({
	endpoint: endpoints.queryBatchOperationItems.getUrl(),
	method: endpoints.queryBatchOperationItems.method,
});

const mockQueryDecisionDefinitionsEndpoint = createEndpointMock({
	endpoint: endpoints.queryDecisionDefinitions.getUrl(),
	method: endpoints.queryDecisionDefinitions.method,
});

const mockQueryDecisionInstancesEndpoint = createEndpointMock({
	endpoint: endpoints.queryDecisionInstances.getUrl(),
	method: endpoints.queryDecisionInstances.method,
});

const mockCreateDecisionInstancesDeletionBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.createDecisionInstancesDeletionBatchOperation.getUrl(),
	method: endpoints.createDecisionInstancesDeletionBatchOperation.method,
});

const mockCurrentUserEndpoint = createEndpointMock({
	endpoint: endpoints.getCurrentUser.getUrl(),
	method: endpoints.getCurrentUser.method,
});

const mockLoginEndpoint = createEndpointMock({
	endpoint: '/login',
	method: 'POST',
});

const mockLogoutEndpoint = createEndpointMock({
	endpoint: '/logout',
	method: 'POST',
});

const mockSystemConfigurationEndpoint = createEndpointMock({
	endpoint: endpoints.getSystemConfiguration.getUrl(),
	method: endpoints.getSystemConfiguration.method,
});

const mockLicenseEndpoint = createEndpointMock({
	endpoint: endpoints.getLicense.getUrl(),
	method: endpoints.getLicense.method,
});

const mockSaasTokenEndpoint = createEndpointMock({
	endpoint: '/v2/authentication/me/token',
	method: 'GET',
});

const mockGetUserTaskEndpoint = createEndpointMock({
	endpoint: endpoints.getUserTask.getUrl({userTaskKey: ':userTaskKey'}),
	method: endpoints.getUserTask.method,
});

const mockQueryVariablesByUserTaskEndpoint = createEndpointMock({
	endpoint: endpoints.queryVariablesByUserTask.getUrl({userTaskKey: ':userTaskKey'}),
	method: endpoints.queryVariablesByUserTask.method,
});

const mockGetVariableEndpoint = createEndpointMock({
	endpoint: endpoints.getVariable.getUrl({variableKey: ':variableKey'}),
	method: endpoints.getVariable.method,
});

const mockGetProcessDefinitionXmlEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessDefinitionXml.getUrl({processDefinitionKey: ':processDefinitionKey'}),
	method: endpoints.getProcessDefinitionXml.method,
});

const mockGetProcessDefinitionStatisticsEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessDefinitionStatistics.getUrl({
		processDefinitionKey: ':processDefinitionKey',
		statisticName: 'element-instances',
	}),
	method: endpoints.getProcessDefinitionStatistics.method,
});

const mockAssignTaskEndpoint = createEndpointMock({
	endpoint: endpoints.assignTask.getUrl({userTaskKey: ':userTaskKey'}),
	method: endpoints.assignTask.method,
});

const mockUnassignTaskEndpoint = createEndpointMock({
	endpoint: endpoints.unassignTask.getUrl({userTaskKey: ':userTaskKey'}),
	method: endpoints.unassignTask.method,
});

const mockCompleteTaskEndpoint = createEndpointMock({
	endpoint: endpoints.completeTask.getUrl({userTaskKey: ':userTaskKey'}),
	method: endpoints.completeTask.method,
});

const mockQueryUserTaskAuditLogsEndpoint = createEndpointMock({
	endpoint: endpoints.queryUserTaskAuditLogs.getUrl({userTaskKey: ':userTaskKey'}),
	method: endpoints.queryUserTaskAuditLogs.method,
});

const mockGetAuditLogEndpoint = createEndpointMock({
	endpoint: endpoints.getAuditLog.getUrl({auditLogKey: ':auditLogKey'}),
	method: endpoints.getAuditLog.method,
});

const mockGetDecisionInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.getDecisionInstance.getUrl({decisionEvaluationInstanceKey: ':decisionEvaluationInstanceKey'}),
	method: endpoints.getDecisionInstance.method,
});

const mockQueryAuditLogsEndpoint = createEndpointMock({
	endpoint: endpoints.queryAuditLogs.getUrl(),
	method: endpoints.queryAuditLogs.method,
});

const mockGetProcessInstanceCallHierarchyEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessInstanceCallHierarchy.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.getProcessInstanceCallHierarchy.method,
});

export {
	mockCurrentUserEndpoint,
	mockLoginEndpoint,
	mockLogoutEndpoint,
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockSaasTokenEndpoint,
	mockGetUserTaskEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockGetVariableEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockAssignTaskEndpoint,
	mockUnassignTaskEndpoint,
	mockCompleteTaskEndpoint,
	mockQueryUserTaskAuditLogsEndpoint,
	mockGetAuditLogEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockGetProcessDefinitionEndpoint,
	mockGetProcessStartFormEndpoint,
	mockCreateProcessInstanceEndpoint,
	mockCreateDocumentsEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceVersionStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint,
	mockQueryBatchOperationsEndpoint,
	mockGetBatchOperationEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockCreateDecisionInstancesDeletionBatchOperationEndpoint,
	mockQueryAuditLogsEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
};
