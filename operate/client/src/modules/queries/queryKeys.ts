/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ElementInstance,
  GetIncidentProcessInstanceStatisticsByDefinitionRequestBody,
  GetIncidentProcessInstanceStatisticsByErrorRequestBody,
  GetProcessDefinitionInstanceStatisticsRequestBody,
  GetProcessDefinitionInstanceVersionStatisticsRequestBody,
  GetProcessDefinitionStatisticsRequestBody,
  ProcessInstance,
  QueryAuditLogsRequestBody,
  QueryBatchOperationItemsRequestBody,
  QueryBatchOperationsRequestBody,
  QueryDecisionInstancesRequestBody,
  QueryElementInstanceIncidentsRequestBody,
  QueryElementInstancesRequestBody,
  QueryJobsRequestBody,
  QueryProcessInstanceIncidentsRequestBody,
  QueryProcessInstancesRequestBody,
  QueryUserTasksRequestBody,
  Variable,
} from '@camunda/camunda-api-zod-schemas/8.10';

const queryKeys = {
  variables: {
    search: () => ['searchVariables'],
    searchWithFilter: (params: {
      processInstanceKey: ProcessInstance['processInstanceKey'];
      scopeKey: Variable['scopeKey'] | null;
      name?: Variable['name'];
      value?: Variable['value'];
    }) => [...queryKeys.variables.search(), ...Object.values(params)],
  },
  decisionInstances: {
    get: (decisionEvaluationInstanceKey: string) => [
      'decisionInstance',
      decisionEvaluationInstanceKey,
    ],
    drdData: (decisionEvaluationKey: string) => [
      'decisionInstanceDrdData',
      decisionEvaluationKey,
    ],
    search: (payload?: QueryDecisionInstancesRequestBody) => [
      'decisionInstancesSearch',
      payload,
    ],
    searchPaginated: (payload?: QueryDecisionInstancesRequestBody) =>
      payload
        ? ['decisionInstancesSearchPaginated', payload]
        : ['decisionInstancesSearchPaginated'],
  },
  decisionDefinitions: {
    search: (payload?: object) =>
      payload
        ? ['decisionDefinitionsSearch', payload]
        : ['decisionDefinitionsSearch'],
  },
  decisionDefinitionXml: {
    get: (decisionDefinitionKey?: string) => [
      'decisionDefinitionXml',
      decisionDefinitionKey,
    ],
  },
  processDefinitionXml: {
    get: (processDefinitionKey?: string) => [
      'processDefinitionXml',
      processDefinitionKey,
    ],
  },
  processDefinitions: {
    get: (processDefinitionKey?: string) => [
      'processDefinition',
      processDefinitionKey,
    ],
    search: (payload?: object) =>
      payload
        ? ['processDefinitionsSearch', payload]
        : ['processDefinitionsSearch'],
  },
  processDefinitionStatistics: {
    getPaginated: (
      payload?: GetProcessDefinitionInstanceStatisticsRequestBody,
    ) => ['processDefinitionStatistics', 'paginated', payload],
    getByVersion: (
      processDefinitionId: string,
      payload?: GetProcessDefinitionInstanceVersionStatisticsRequestBody,
    ) => ['processDefinitionVersionStatistics', processDefinitionId, payload],
    runningInstancesCount: () => [
      'processDefinitionStatistics',
      'runningInstancesCount',
    ],
    get: (
      processDefinitionKey: string | undefined,
      payload: GetProcessDefinitionStatisticsRequestBody,
    ) => ['processDefinitionStatistics', processDefinitionKey, payload],
  },
  incidents: {
    get: (incidentKey: string) => ['incident', incidentKey],
    search: () => ['incidentsSearch'],
    processInstanceIncidentsCount: (
      processInstanceKey: string,
      filter?: QueryProcessInstanceIncidentsRequestBody['filter'],
    ) => [
      queryKeys.incidents.search()[0],
      'processInstanceIncidentsCount',
      processInstanceKey,
      filter,
    ],
    elementInstanceIncidentsCount: (elementInstanceKey: string) => [
      queryKeys.incidents.search()[0],
      'elementInstanceIncidentsCount',
      elementInstanceKey,
    ],
    searchByProcessInstanceKeyPaginated: (
      processInstanceKey: string,
      payload?: QueryProcessInstanceIncidentsRequestBody,
    ) => [
      queryKeys.incidents.search()[0],
      'searchByProcessInstanceKeyPaginated',
      processInstanceKey,
      payload,
    ],
    searchByElementInstanceKeyPaginated: (
      elementInstanceKey: string,
      payload?: QueryElementInstanceIncidentsRequestBody,
    ) => [
      queryKeys.incidents.search()[0],
      'searchByElementInstanceKeyPaginated',
      elementInstanceKey,
      payload,
    ],
  },
  elementInstances: {
    search: (payload: {
      elementId: string;
      processInstanceKey: string;
      elementType?: ElementInstance['type'];
    }) => {
      const {elementId, processInstanceKey, elementType} = payload;

      return [
        'elementInstancesSearch',
        elementId,
        processInstanceKey,
        elementType,
      ];
    },
    searchByScope: (
      payload: Pick<QueryElementInstancesRequestBody, 'page' | 'sort'> & {
        filter: {elementInstanceScopeKey: string};
      },
    ) => {
      return ['elementInstancesSearchByScope', ...Object.values(payload)];
    },
  },
  processInstances: {
    base: () => ['processInstances'],
    search: (payload: QueryProcessInstancesRequestBody) => [
      'processInstancesSearch',
      payload,
    ],
    searchPaginated: (payload: QueryProcessInstancesRequestBody) => [
      'processInstances',
      'search',
      'paginated',
      payload,
    ],
    runningInstancesCount: (processDefinitionKey: string) => [
      'processInstances',
      'runningInstancesCount',
      processDefinitionKey,
    ],
  },
  batchOperations: {
    query: (payload?: QueryBatchOperationsRequestBody) =>
      payload !== undefined
        ? ['batchOperations', payload]
        : ['batchOperations'],
    get: (batchOperationKey: string) => ['batchOperation', batchOperationKey],
  },
  batchOperationItems: {
    query: (payload: QueryBatchOperationItemsRequestBody) => [
      'batchOperationItems',
      payload,
    ],
  },
  auditLogs: {
    search: (payload?: QueryAuditLogsRequestBody) => [
      'auditLogsSearch',
      payload,
    ],
  },
  incidentProcessInstanceStatisticsByError: {
    getPaginated: (
      payload?: GetIncidentProcessInstanceStatisticsByErrorRequestBody,
    ) => ['incidentProcessInstanceStatisticsByError', 'paginated', payload],
  },
  incidentProcessInstanceStatisticsByDefinition: {
    get: (
      payload?: GetIncidentProcessInstanceStatisticsByDefinitionRequestBody,
    ) => ['incidentProcessInstanceStatisticsByDefinition', payload],
  },
  currentUser: {
    get: () => ['currentUser'],
  },
  userTasks: {
    queryUserTasks: (payload: QueryUserTasksRequestBody) => [
      'userTasks',
      payload,
    ],
  },
  variable: {
    get: (variableKey: string) => ['variable', variableKey],
  },
  elementInstance: {
    get: (elementInstanceKey: string) => [
      'elementInstance',
      elementInstanceKey,
    ],
  },
  elementInstancesStatistics: {
    get: (processInstanceKey: string) => [
      'elementInstancesStatistics',
      processInstanceKey,
    ],
  },
  callHierarchy: {
    get: (processInstanceKey: string) => ['callHierarchy', processInstanceKey],
  },
  sequenceFlows: {
    get: (processInstanceKey: string) => ['sequenceFlows', processInstanceKey],
  },
  jobs: {
    search: (payload: QueryJobsRequestBody) => ['jobsSearch', payload],
  },
  processInstance: {
    get: (processInstanceKey: string) => [
      'processInstance',
      processInstanceKey,
    ],
  },
};

export {queryKeys};
