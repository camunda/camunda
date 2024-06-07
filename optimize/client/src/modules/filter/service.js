/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {post, get} from 'request';
import equal from 'fast-deep-equal';

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
    'instanceStartDate',
    'instanceEndDate',
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
    ({type, filterLevel, appliedTo}) =>
      !(
        newFilter.filterLevel === filterLevel &&
        newFilter.type === type &&
        equal(newFilter.appliedTo, appliedTo) &&
        uniqueFilters.includes(type)
      )
  );
}

export async function loadUserNames(type, ids) {
  const response = await get(`api/${type}`, {idIn: ids.join(',')});

  return await response.json();
}
