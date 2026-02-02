/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ProcessInstance,
  Variable,
} from '@camunda/camunda-api-zod-schemas/8.8';

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
<<<<<<< HEAD
=======
    search: (payload?: object) =>
      payload
        ? ['processDefinitionsSearch', payload]
        : ['processDefinitionsSearch'],
  },
  processDefinitionStatistics: {
    get: (payload?: GetProcessDefinitionInstanceStatisticsRequestBody) => [
      'processDefinitionStatistics',
      payload,
    ],
    getByVersion: (
      processDefinitionId: string,
      payload?: GetProcessDefinitionInstanceVersionStatisticsRequestBody,
    ) => ['processDefinitionVersionStatistics', processDefinitionId, payload],
    runningInstancesCount: () => [
      'processDefinitionStatistics',
      'runningInstancesCount',
    ],
  },
  incidents: {
    get: (incidentKey: string) => ['incident', incidentKey],
    search: () => ['incidentsSearch'],
    processInstanceIncidentsCount: (processInstanceKey: string) => [
      queryKeys.incidents.search()[0],
      'processInstanceIncidentsCount',
      processInstanceKey,
    ],
    searchByElementInstanceKey: (
      elementInstanceKey: string,
      payload?: QueryElementInstanceIncidentsRequestBody,
    ) => [
      queryKeys.incidents.search()[0],
      'searchByElementInstanceKey',
      elementInstanceKey,
      payload,
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
    searchByProcessInstanceKey: (processInstanceKey?: string) => [
      'batchOperationItemsSearchByProcessInstanceKey',
      processInstanceKey,
    ],
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
    get: (payload?: GetIncidentProcessInstanceStatisticsByErrorRequestBody) => [
      'incidentProcessInstanceStatisticsByError',
      payload,
    ],
  },
  incidentProcessInstanceStatisticsByDefinition: {
    get: (
      payload?: GetIncidentProcessInstanceStatisticsByDefinitionRequestBody,
    ) => ['incidentProcessInstanceStatisticsByDefinition', payload],
  },
  messageSubscriptions: {
    search: (payload: QueryMessageSubscriptionsRequestBody) => [
      'messageSubscriptionsSearch',
      payload,
    ],
  },
  currentUser: {
    get: () => ['currentUser'],
  },
  userTasks: {
    getByElementInstance: (elementInstanceKey: string) => [
      'userTasksByElementInstance',
      elementInstanceKey,
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
  flowNodeInstancesStatistics: {
    get: (processInstanceKey: string) => [
      'flowNodeInstancesStatistics',
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
>>>>>>> 6f338e3d (fix: AHSP instance child fetching)
  },
};

export {queryKeys};
