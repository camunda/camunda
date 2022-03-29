/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, put, post} from 'request';

export async function loadProcesses(sortBy, sortOrder) {
  const params = {};
  if (sortBy && sortOrder) {
    params.sortBy = sortBy;
    params.sortOrder = sortOrder;
  }

  const response = await get('api/process/goals', params);

  return await response.json();
}

export async function loadTenants(key) {
  const response = await post(`api/definition/process/_resolveTenantsForVersions`, {
    definitions: [{key, versions: 'all'}],
  });

  return await response.json();
}

export function updateGoals(processDefinitionKey, goals) {
  return put(`api/process/${processDefinitionKey}/goals`, goals);
}

export async function evaluateGoals(processDefinitionKey, goals) {
  const response = await post(`api/process/${processDefinitionKey}/goals/evaluate`, goals);

  return await response.json();
}
