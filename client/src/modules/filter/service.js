/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post, get} from 'request';

export async function loadValues(
  processDefinitionKey,
  processDefinitionVersions,
  tenantIds,
  name,
  type,
  resultOffset,
  numResults,
  valueFilter
) {
  const response = await post(`api/variables/values`, {
    processDefinitionKey,
    processDefinitionVersions,
    tenantIds,
    name,
    type,
    resultOffset,
    numResults,
    valueFilter,
  });

  return await response.json();
}

export async function loadDecisionValues(
  type,
  decisionDefinitionKey,
  decisionDefinitionVersions,
  tenantIds,
  variableId,
  variableType,
  resultOffset,
  numResults,
  valueFilter
) {
  const endpoint = type === 'inputVariable' ? 'inputs' : 'outputs';

  const response = await post(`api/decision-variables/${endpoint}/values`, {
    decisionDefinitionKey,
    decisionDefinitionVersions,
    tenantIds,
    variableId,
    variableType,
    resultOffset,
    numResults,
    valueFilter,
  });

  return await response.json();
}

export function filterSameTypeExistingFilters(filters, newFilter) {
  const uniqueFilters = [
    'runningInstancesOnly',
    'completedInstancesOnly',
    'canceledInstancesOnly',
    'nonCanceledInstancesOnly',
    'suspendedInstancesOnly',
    'nonSuspendedInstancesOnly',
    'startDate',
    'endDate',
    'runningFlowNodesOnly',
    'completedFlowNodesOnly',
    'canceledFlowNodesOnly',
    'completedOrCanceledFlowNodesOnly',
    'doesNotIncludeIncident',
    'includesOpenIncident',
    'includesResolvedIncident',
    'evaluationDateTime',
  ];

  return filters.filter(
    ({type, filterLevel}) =>
      !(
        newFilter.filterLevel === filterLevel &&
        newFilter.type === type &&
        uniqueFilters.includes(type)
      )
  );
}

export async function loadUserNames(type, ids) {
  const response = await get(`api/${type}`, {idIn: ids.join(',')});

  return await response.json();
}
