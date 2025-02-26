/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {post, get} from 'request';

export async function getVariableNames(reportIds) {
  const response = await post('api/variables/reports', {reportIds});

  return await response.json();
}

export async function getVariableValues(reportIds, name, type, numResults, valueFilter) {
  const response = await post('api/variables/values/reports', {
    reportIds,
    name,
    type,
    valueFilter,
    numResults,
    resultOffset: 0,
  });

  return await response.json();
}

export async function loadUsersByReportIds(type, payload) {
  const response = await post(`api/${type}/search/reports`, payload);

  return await response.json();
}

export async function getAssigneeNames(type, listOfIds) {
  const response = await get('api/' + type, {idIn: listOfIds.join(',')});

  return await response.json();
}

export function isOfType(filter, availableFilter) {
  switch (filter.type) {
    case 'runningInstancesOnly':
    case 'completedInstancesOnly':
    case 'canceledInstancesOnly':
    case 'nonCanceledInstancesOnly':
    case 'suspendedInstancesOnly':
    case 'nonSuspendedInstancesOnly':
      return availableFilter.type === 'state';
    case 'instanceStartDate':
    case 'instanceEndDate':
    case 'assignee':
      return availableFilter.type === filter.type;
    case 'variable':
      return (
        availableFilter.type === 'variable' &&
        availableFilter.data.type === filter.data.type &&
        availableFilter.data.name === filter.data.name
      );
    default:
      return false;
  }
}
