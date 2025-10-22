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
  QueryElementInstanceIncidentsRequestBody,
  QueryProcessInstanceIncidentsRequestBody,
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
    searchByProcessInstanceKey: (
      processInstanceKey: string,
      payload?: QueryProcessInstanceIncidentsRequestBody,
    ) => [
      queryKeys.incidents.search()[0],
      'searchByProcessInstanceKey',
      processInstanceKey,
      payload,
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
    searcyByScope: (payload: {elementInstanceScopeKey: string}) => {
      const {elementInstanceScopeKey} = payload;
      return ['elementInstancesSearch', elementInstanceScopeKey];
    },
  },
};

export {queryKeys};
