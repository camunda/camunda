/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ElementInstance,
  ProcessInstance,
  QueryDecisionInstancesRequestBody,
  QueryElementInstanceIncidentsRequestBody,
  QueryElementInstancesRequestBody,
  QueryProcessInstanceIncidentsRequestBody,
  QueryProcessInstancesRequestBody,
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
    searchPaginated: (payload?: QueryDecisionInstancesRequestBody) => [
      'decisionInstancesSearchPaginated',
      payload,
    ],
  },
  decisionDefinitions: {
    get: (decisionDefinitionKey: string) => [
      'decisionDefinition',
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
      elementType: ElementInstance['type'];
      pageSize: number;
    }) => {
      const {elementId, processInstanceKey, elementType, pageSize} = payload;

      return [
        'elementInstancesSearch',
        elementId,
        processInstanceKey,
        elementType,
        pageSize,
      ];
    },
    searchByScope: (
      payload: Pick<QueryElementInstancesRequestBody, 'page' | 'sort'> & {
        elementInstanceScopeKey: string;
      },
    ) => {
      return ['elementInstancesSearchByScope', ...Object.values(payload)];
    },
  },
  processInstances: {
    searchPaginated: (payload: QueryProcessInstancesRequestBody) => [
      'processInstances',
      'search',
      'paginated',
      payload,
    ],
  },
};

export {queryKeys};
