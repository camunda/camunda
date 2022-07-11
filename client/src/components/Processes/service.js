/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, put} from 'request';

import {formatters} from 'services';

export async function loadProcesses(sortBy, sortOrder) {
  const params = {};
  if (sortBy && sortOrder) {
    params.sortBy = sortBy;
    params.sortOrder = sortOrder;
  }

  const response = await get('api/process/overview', params);
  return await response.json();
}

export function updateProcess(processDefinitionKey, payload) {
  return put(`api/process/${processDefinitionKey}`, payload);
}

export async function loadManagementDashboard() {
  const response = await get(`api/dashboard/management`);

  return await response.json();
}

export function isSuccessful({target, unit, value, isBelow, measure}) {
  const actualValue = Number(value);
  const targetMs =
    measure === 'duration' && unit
      ? formatters.convertToMilliseconds(target, unit)
      : Number(target);

  return isBelow ? targetMs > actualValue : targetMs < actualValue;
}
