/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
