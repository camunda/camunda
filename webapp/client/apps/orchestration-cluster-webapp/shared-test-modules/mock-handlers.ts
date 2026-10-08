/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {
	endpoints,
	queryProcessDefinitionsRequestBodySchema,
	type QueryProcessDefinitionsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
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

const mockQueryProcessDefinitionsByFilterEndpoint = ({
	getResponse,
}: {
	getResponse: (filter: QueryProcessDefinitionsRequestBody['filter']) => Response;
}) =>
	http.post(endpoints.queryProcessDefinitions.getUrl(), async ({request}) => {
		const {filter} = queryProcessDefinitionsRequestBodySchema.parse(await request.json());
		return getResponse(filter);
	});

const mockQueryMessageSubscriptionsEndpoint = createEndpointMock({
	endpoint: endpoints.queryMessageSubscriptions.getUrl(),
	method: endpoints.queryMessageSubscriptions.method,
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

const mockSuspendBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.suspendBatchOperation.getUrl({batchOperationKey: ':batchOperationKey'}),
	method: endpoints.suspendBatchOperation.method,
});

const mockResumeBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.resumeBatchOperation.getUrl({batchOperationKey: ':batchOperationKey'}),
	method: endpoints.resumeBatchOperation.method,
});

const mockCancelBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.cancelBatchOperation.getUrl({batchOperationKey: ':batchOperationKey'}),
	method: endpoints.cancelBatchOperation.method,
});

const mockResolveProcessInstanceIncidentsEndpoint = createEndpointMock({
	endpoint: endpoints.resolveProcessInstanceIncidents.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.resolveProcessInstanceIncidents.method,
});

const mockCancelProcessInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.cancelProcessInstance.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.cancelProcessInstance.method,
});

const mockDeleteProcessInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.deleteProcessInstance.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.deleteProcessInstance.method,
});

const mockSuspendProcessInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.suspendProcessInstance.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.suspendProcessInstance.method,
});

const mockResumeProcessInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.resumeProcessInstance.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.resumeProcessInstance.method,
});

const mockQueryBatchOperationItemsEndpoint = createEndpointMock({
	endpoint: endpoints.queryBatchOperationItems.getUrl(),
	method: endpoints.queryBatchOperationItems.method,
});

const mockQueryProcessInstancesEndpoint = createEndpointMock({
	endpoint: endpoints.queryProcessInstances.getUrl(),
	method: endpoints.queryProcessInstances.method,
});

const mockQueryElementInstancesEndpoint = createEndpointMock({
	endpoint: endpoints.queryElementInstances.getUrl(),
	method: endpoints.queryElementInstances.method,
});

const mockGetElementInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.getElementInstance.getUrl({elementInstanceKey: ':elementInstanceKey'}),
	method: endpoints.getElementInstance.method,
});

const mockQueryAgentInstancesEndpoint = createEndpointMock({
	endpoint: endpoints.queryAgentInstances.getUrl(),
	method: endpoints.queryAgentInstances.method,
});

const mockGetProcessInstanceEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessInstance.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.getProcessInstance.method,
});

const mockQueryProcessInstanceIncidentsEndpoint = createEndpointMock({
	endpoint: endpoints.queryProcessInstanceIncidents.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.queryProcessInstanceIncidents.method,
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

const mockDeleteResourceEndpoint = createEndpointMock({
	endpoint: endpoints.deleteResource.getUrl({resourceKey: ':resourceKey'}),
	method: endpoints.deleteResource.method,
});

const mockGetDecisionDefinitionXmlEndpoint = createEndpointMock({
	endpoint: endpoints.getDecisionDefinitionXml.getUrl({decisionDefinitionKey: ':decisionDefinitionKey'}),
	method: endpoints.getDecisionDefinitionXml.method,
});

const mockCurrentUserEndpoint = createEndpointMock({
	endpoint: endpoints.getCurrentUser.getUrl(),
	method: endpoints.getCurrentUser.method,
});

const mockLoginCsrfTokenEndpoint = createEndpointMock({
	endpoint: '/login',
	method: 'GET',
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

const mockGetUserTaskFormEndpoint = createEndpointMock({
	endpoint: endpoints.getUserTaskForm.getUrl({userTaskKey: ':userTaskKey'}),
	method: endpoints.getUserTaskForm.method,
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

const mockGetProcessDefinitionXmlByKeyEndpoint = ({xmlByKey}: {xmlByKey: Record<string, string>}) =>
	http.get(endpoints.getProcessDefinitionXml.getUrl({processDefinitionKey: ':processDefinitionKey'}), ({params}) => {
		const xml = xmlByKey[String(params.processDefinitionKey)];
		return xml === undefined ? new HttpResponse(null, {status: 404}) : HttpResponse.text(xml);
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

const mockGetProcessInstanceWaitStateStatisticsEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessInstanceWaitStateStatistics.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.getProcessInstanceWaitStateStatistics.method,
});

const mockGetProcessInstanceStatisticsEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessInstanceStatistics.getUrl({
		processInstanceKey: ':processInstanceKey',
		statisticName: 'element-instances',
	}),
	method: endpoints.getProcessInstanceStatistics.method,
});

const mockGetProcessInstanceSequenceFlowsEndpoint = createEndpointMock({
	endpoint: endpoints.getProcessInstanceSequenceFlows.getUrl({processInstanceKey: ':processInstanceKey'}),
	method: endpoints.getProcessInstanceSequenceFlows.method,
});

const mockQueryMappingRulesEndpoint = createEndpointMock({
	endpoint: endpoints.queryMappingRules.getUrl(),
	method: endpoints.queryMappingRules.method,
});

const mockCreateMappingRuleEndpoint = createEndpointMock({
	endpoint: endpoints.createMappingRule.getUrl(),
	method: endpoints.createMappingRule.method,
});

const mockUpdateMappingRuleEndpoint = createEndpointMock({
	endpoint: endpoints.updateMappingRule.getUrl({mappingRuleId: ':mappingRuleId'}),
	method: endpoints.updateMappingRule.method,
});

const mockDeleteMappingRuleEndpoint = createEndpointMock({
	endpoint: endpoints.deleteMappingRule.getUrl({mappingRuleId: ':mappingRuleId'}),
	method: endpoints.deleteMappingRule.method,
});

const mockQueryClusterVariablesEndpoint = createEndpointMock({
	endpoint: endpoints.searchClusterVariables.getUrl(),
	method: endpoints.searchClusterVariables.method,
});

const mockGetGlobalClusterVariableEndpoint = createEndpointMock({
	endpoint: endpoints.getGlobalClusterVariable.getUrl({name: ':name'}),
	method: endpoints.getGlobalClusterVariable.method,
});

const mockGetTenantClusterVariableEndpoint = createEndpointMock({
	endpoint: endpoints.getTenantClusterVariable.getUrl({tenantId: ':tenantId', name: ':name'}),
	method: endpoints.getTenantClusterVariable.method,
});

const mockCreateGlobalClusterVariableEndpoint = createEndpointMock({
	endpoint: endpoints.createGlobalClusterVariable.getUrl(),
	method: endpoints.createGlobalClusterVariable.method,
});

const mockCreateTenantClusterVariableEndpoint = createEndpointMock({
	endpoint: endpoints.createTenantClusterVariable.getUrl({tenantId: ':tenantId'}),
	method: endpoints.createTenantClusterVariable.method,
});

const mockUpdateGlobalClusterVariableEndpoint = createEndpointMock({
	endpoint: endpoints.updateGlobalClusterVariable.getUrl({name: ':name'}),
	method: endpoints.updateGlobalClusterVariable.method,
});

const mockDeleteGlobalClusterVariableEndpoint = createEndpointMock({
	endpoint: endpoints.deleteGlobalClusterVariable.getUrl({name: ':name'}),
	method: endpoints.deleteGlobalClusterVariable.method,
});

const mockQueryTenantsEndpoint = createEndpointMock({
	endpoint: endpoints.queryTenants.getUrl(),
	method: endpoints.queryTenants.method,
});
const mockSearchGlobalTaskListenersEndpoint = createEndpointMock({
	endpoint: endpoints.searchGlobalTaskListeners.getUrl(),
	method: endpoints.searchGlobalTaskListeners.method,
});

const mockGetGlobalTaskListenerEndpoint = createEndpointMock({
	endpoint: endpoints.getGlobalTaskListener.getUrl({id: ':id'}),
	method: endpoints.getGlobalTaskListener.method,
});

const mockCreateGlobalTaskListenerEndpoint = createEndpointMock({
	endpoint: endpoints.createGlobalTaskListener.getUrl(),
	method: endpoints.createGlobalTaskListener.method,
});

const mockUpdateGlobalTaskListenerEndpoint = createEndpointMock({
	endpoint: endpoints.updateGlobalTaskListener.getUrl({id: ':id'}),
	method: endpoints.updateGlobalTaskListener.method,
});

const mockDeleteGlobalTaskListenerEndpoint = createEndpointMock({
	endpoint: endpoints.deleteGlobalTaskListener.getUrl({id: ':id'}),
	method: endpoints.deleteGlobalTaskListener.method,
});

const mockCreateCancellationBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.createCancellationBatchOperation.getUrl(),
	method: endpoints.createCancellationBatchOperation.method,
});

const mockCreateIncidentResolutionBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.createIncidentResolutionBatchOperation.getUrl(),
	method: endpoints.createIncidentResolutionBatchOperation.method,
});

const mockCreateDeletionBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.createDeletionBatchOperation.getUrl(),
	method: endpoints.createDeletionBatchOperation.method,
});

const mockCreateSuspensionBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.suspendProcessInstancesBatchOperation.getUrl(),
	method: endpoints.suspendProcessInstancesBatchOperation.method,
});

const mockCreateResumptionBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.resumeProcessInstancesBatchOperation.getUrl(),
	method: endpoints.resumeProcessInstancesBatchOperation.method,
});

const mockCreateModificationBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.createModificationBatchOperation.getUrl(),
	method: endpoints.createModificationBatchOperation.method,
});

const mockCreateMigrationBatchOperationEndpoint = createEndpointMock({
	endpoint: endpoints.createMigrationBatchOperation.getUrl(),
	method: endpoints.createMigrationBatchOperation.method,
});

const mockQueryUsersEndpoint = createEndpointMock({
	endpoint: endpoints.queryUsers.getUrl(),
	method: endpoints.queryUsers.method,
});

const mockGetUserEndpoint = createEndpointMock({
	endpoint: endpoints.getUser.getUrl({username: ':username'}),
	method: endpoints.getUser.method,
});

const mockCreateUserEndpoint = createEndpointMock({
	endpoint: endpoints.createUser.getUrl(),
	method: endpoints.createUser.method,
});

const mockUpdateUserEndpoint = createEndpointMock({
	endpoint: endpoints.updateUser.getUrl({username: ':username'}),
	method: endpoints.updateUser.method,
});

const mockDeleteUserEndpoint = createEndpointMock({
	endpoint: endpoints.deleteUser.getUrl({username: ':username'}),
	method: endpoints.deleteUser.method,
});

const mockQueryAuthorizationsEndpoint = createEndpointMock({
	endpoint: endpoints.queryAuthorizations.getUrl(),
	method: endpoints.queryAuthorizations.method,
});

const mockGetAuthorizationEndpoint = createEndpointMock({
	endpoint: endpoints.getAuthorization.getUrl({authorizationKey: ':authorizationKey'}),
	method: endpoints.getAuthorization.method,
});

const mockCreateAuthorizationEndpoint = createEndpointMock({
	endpoint: endpoints.createAuthorization.getUrl(),
	method: endpoints.createAuthorization.method,
});

const mockDeleteAuthorizationEndpoint = createEndpointMock({
	endpoint: endpoints.deleteAuthorization.getUrl({authorizationKey: ':authorizationKey'}),
	method: endpoints.deleteAuthorization.method,
});

const mockQueryRolesEndpoint = createEndpointMock({
	endpoint: endpoints.queryRoles.getUrl(),
	method: endpoints.queryRoles.method,
});

const mockQueryGroupsEndpoint = createEndpointMock({
	endpoint: endpoints.queryGroups.getUrl(),
	method: endpoints.queryGroups.method,
});

const mockGetGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.getGroup.getUrl({groupId: ':groupId'})),
	method: endpoints.getGroup.method,
});

const mockCreateGroupEndpoint = createEndpointMock({
	endpoint: endpoints.createGroup.getUrl(),
	method: endpoints.createGroup.method,
});

const mockUpdateGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.updateGroup.getUrl({groupId: ':groupId'})),
	method: endpoints.updateGroup.method,
});

const mockDeleteGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.deleteGroup.getUrl({groupId: ':groupId'})),
	method: endpoints.deleteGroup.method,
});

const mockQueryUsersByGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.queryUsersByGroup.getUrl({groupId: ':groupId'})),
	method: endpoints.queryUsersByGroup.method,
});

const mockQueryClientsByGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.queryClientsByGroup.getUrl({groupId: ':groupId'})),
	method: endpoints.queryClientsByGroup.method,
});

const mockQueryRolesByGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.queryRolesByGroup.getUrl({groupId: ':groupId'})),
	method: endpoints.queryRolesByGroup.method,
});

const mockQueryMappingRulesByGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.queryMappingRulesByGroup.getUrl({groupId: ':groupId'})),
	method: endpoints.queryMappingRulesByGroup.method,
});

const mockAssignUserToGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.assignUserToGroup.getUrl({groupId: ':groupId', username: ':username'})),
	method: endpoints.assignUserToGroup.method,
});

const mockUnassignUserFromGroupEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.unassignUserFromGroup.getUrl({groupId: ':groupId', username: ':username'})),
	method: endpoints.unassignUserFromGroup.method,
});

const mockAssignGroupToRoleEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.assignGroupToRole.getUrl({roleId: ':roleId', groupId: ':groupId'})),
	method: endpoints.assignGroupToRole.method,
});

const mockUnassignGroupFromRoleEndpoint = createEndpointMock({
	endpoint: decodeURIComponent(endpoints.unassignGroupFromRole.getUrl({roleId: ':roleId', groupId: ':groupId'})),
	method: endpoints.unassignGroupFromRole.method,
});

export {
	mockCreateCancellationBatchOperationEndpoint,
	mockCreateMigrationBatchOperationEndpoint,
	mockCreateIncidentResolutionBatchOperationEndpoint,
	mockCreateDeletionBatchOperationEndpoint,
	mockCreateSuspensionBatchOperationEndpoint,
	mockCreateResumptionBatchOperationEndpoint,
	mockCreateModificationBatchOperationEndpoint,
	mockCurrentUserEndpoint,
	mockLoginCsrfTokenEndpoint,
	mockLoginEndpoint,
	mockLogoutEndpoint,
	mockSystemConfigurationEndpoint,
	mockLicenseEndpoint,
	mockSaasTokenEndpoint,
	mockGetUserTaskEndpoint,
	mockGetUserTaskFormEndpoint,
	mockQueryVariablesByUserTaskEndpoint,
	mockGetVariableEndpoint,
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessDefinitionXmlByKeyEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
	mockAssignTaskEndpoint,
	mockUnassignTaskEndpoint,
	mockCompleteTaskEndpoint,
	mockQueryUserTaskAuditLogsEndpoint,
	mockGetAuditLogEndpoint,
	mockQueryUserTasksEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessDefinitionsByFilterEndpoint,
	mockQueryMessageSubscriptionsEndpoint,
	mockGetProcessDefinitionEndpoint,
	mockGetProcessStartFormEndpoint,
	mockCreateProcessInstanceEndpoint,
	mockCreateDocumentsEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceVersionStatisticsEndpoint,
	mockGetIncidentProcessInstanceStatisticsByDefinitionEndpoint,
	mockQueryBatchOperationsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockQueryElementInstancesEndpoint,
	mockGetElementInstanceEndpoint,
	mockQueryAgentInstancesEndpoint,
	mockGetProcessInstanceEndpoint,
	mockQueryProcessInstanceIncidentsEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockGetBatchOperationEndpoint,
	mockSuspendBatchOperationEndpoint,
	mockResumeBatchOperationEndpoint,
	mockCancelBatchOperationEndpoint,
	mockResolveProcessInstanceIncidentsEndpoint,
	mockCancelProcessInstanceEndpoint,
	mockDeleteProcessInstanceEndpoint,
	mockSuspendProcessInstanceEndpoint,
	mockResumeProcessInstanceEndpoint,
	mockGetDecisionInstanceEndpoint,
	mockQueryDecisionDefinitionsEndpoint,
	mockQueryDecisionInstancesEndpoint,
	mockCreateDecisionInstancesDeletionBatchOperationEndpoint,
	mockGetDecisionDefinitionXmlEndpoint,
	mockDeleteResourceEndpoint,
	mockQueryAuditLogsEndpoint,
	mockGetProcessInstanceCallHierarchyEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockGetProcessInstanceStatisticsEndpoint,
	mockGetProcessInstanceSequenceFlowsEndpoint,
	mockQueryMappingRulesEndpoint,
	mockCreateMappingRuleEndpoint,
	mockUpdateMappingRuleEndpoint,
	mockDeleteMappingRuleEndpoint,
	mockQueryUsersEndpoint,
	mockQueryAuthorizationsEndpoint,
	mockGetAuthorizationEndpoint,
	mockCreateAuthorizationEndpoint,
	mockDeleteAuthorizationEndpoint,
	mockQueryRolesEndpoint,
	mockQueryGroupsEndpoint,
	mockGetUserEndpoint,
	mockCreateUserEndpoint,
	mockUpdateUserEndpoint,
	mockDeleteUserEndpoint,
	mockGetGroupEndpoint,
	mockCreateGroupEndpoint,
	mockUpdateGroupEndpoint,
	mockDeleteGroupEndpoint,
	mockQueryUsersByGroupEndpoint,
	mockQueryClientsByGroupEndpoint,
	mockQueryRolesByGroupEndpoint,
	mockQueryMappingRulesByGroupEndpoint,
	mockAssignUserToGroupEndpoint,
	mockUnassignUserFromGroupEndpoint,
	mockAssignGroupToRoleEndpoint,
	mockUnassignGroupFromRoleEndpoint,
	mockQueryClusterVariablesEndpoint,
	mockGetGlobalClusterVariableEndpoint,
	mockGetTenantClusterVariableEndpoint,
	mockCreateGlobalClusterVariableEndpoint,
	mockCreateTenantClusterVariableEndpoint,
	mockUpdateGlobalClusterVariableEndpoint,
	mockDeleteGlobalClusterVariableEndpoint,
	mockQueryTenantsEndpoint,
	mockSearchGlobalTaskListenersEndpoint,
	mockGetGlobalTaskListenerEndpoint,
	mockCreateGlobalTaskListenerEndpoint,
	mockUpdateGlobalTaskListenerEndpoint,
	mockDeleteGlobalTaskListenerEndpoint,
};
