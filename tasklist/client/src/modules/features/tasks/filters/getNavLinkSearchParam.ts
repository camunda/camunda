/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getStateLocally} from 'modules/local-storage';
import {prepareCustomFiltersParams} from 'modules/tasks/filters/prepareCustomFiltersParams';
import {type TaskFilters} from 'modules/features/tasks/filters/useTaskFilters';
import difference from 'lodash/difference';

function getCustomFilterParams(options: {username: string; filter: string}) {
  const {username, filter} = options;
  const customFilters = getStateLocally('customFilters') ?? {};
  const filters = customFilters[filter];

  return filters === undefined
    ? {}
    : prepareCustomFiltersParams(filters, username);
}

function getNavLinkSearchParam(options: {
  currentParams: URLSearchParams;
  filter: TaskFilters['filter'];
  username: string;
}): string {
  const CUSTOM_FILTERS_PARAMS = [
    'state',
    'followUpDateFrom',
    'followUpDateTo',
    'dueDateFrom',
    'dueDateTo',
    'assigned',
    'assignee',
    'taskDefinitionId',
    'elementId',
    'candidateGroup',
    'candidateUser',
    'processDefinitionKey',
    'processInstanceKey',
    'tenantIds',
    'tenantId',
    'taskVariables',
  ] as const;
  const {filter, username, currentParams} = options;
  const {sortBy, ...convertedParams} = Object.fromEntries(
    currentParams.entries(),
  );
  const NON_CUSTOM_FILTERS = [
    'all-open',
    'unassigned',
    'assigned-to-me',
    'completed',
  ];
  const customFilterParams = NON_CUSTOM_FILTERS.includes(filter)
    ? {}
    : getCustomFilterParams({username, filter});

  const updatedParams = new URLSearchParams({
    ...convertedParams,
    ...customFilterParams,
    filter,
  });

  if (sortBy !== undefined && sortBy !== 'completion') {
    updatedParams.set('sortBy', sortBy);
  }

  if (filter === 'completed') {
    updatedParams.set('sortBy', 'completion');
  }

  difference(CUSTOM_FILTERS_PARAMS, Object.keys(customFilterParams)).forEach(
    (param) => {
      updatedParams.delete(param);
    },
  );

  return updatedParams.toString();
}
export {getNavLinkSearchParam};
