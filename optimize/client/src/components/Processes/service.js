/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
