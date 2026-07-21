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

const mockGetProcessDefinitionXmlEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessDefinitionXml.getUrl({processDefinitionKey: ':processDefinitionKey'}),
	method: endpoints.getProcessDefinitionXml.method,
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

export {
	mockCurrentUserEndpoint,
	mockLoginEndpoint,
	mockLogoutEndpoint,
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockSaasTokenEndpoint,
	mockGetUserTaskEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockAssignTaskEndpoint,
	mockUnassignTaskEndpoint,
	mockCompleteTaskEndpoint,
	mockQueryUserTaskAuditLogsEndpoint,
	mockGetAuditLogEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceVersionStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint,
	mockQueryBatchOperationsEndpoint,
	mockGetDecisionInstanceEndpoint,
};
