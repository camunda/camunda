/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get} from 'request';

export async function loadVariables(processDefinitionKey, processDefinitionVersion) {
  const response = await get(`api/variables`, {
    processDefinitionKey,
    processDefinitionVersion,
    sortOrder: 'asc',
    orderBy: 'name'
  });

  return await response.json();
}

export async function loadValues(
  processDefinitionKey,
  processDefinitionVersion,
  name,
  type,
  resultOffset,
  numResults,
  valueFilter
) {
  const response = await get(`api/variables/values`, {
    processDefinitionKey,
    processDefinitionVersion,
    name,
    type,
    resultOffset,
    numResults,
    valueFilter
  });

  return await response.json();
}

export async function loadDecisionValues(
  type,
  decisionDefinitionKey,
  decisionDefinitionVersion,
  variableId,
  variableType,
  resultOffset,
  numResults,
  valueFilter
) {
  const endpoint = type === 'inputVariable' ? 'inputs' : 'outputs';

  const response = await get(`api/decision-variables/${endpoint}/values`, {
    decisionDefinitionKey,
    decisionDefinitionVersion,
    variableId,
    variableType,
    resultOffset,
    numResults,
    valueFilter
  });

  return await response.json();
}

export function filterIncompatibleExistingFilters(filters, newFilterType, uniqueTypes) {
  if (uniqueTypes.includes(newFilterType)) {
    return filters.filter(({type}) => !uniqueTypes.includes(type));
  }
  return filters;
}
