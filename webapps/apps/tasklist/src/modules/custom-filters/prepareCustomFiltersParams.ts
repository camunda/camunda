/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatRFC3339} from 'date-fns';
import type {CustomFilters} from './customFiltersSchema';

function prepareCustomFiltersParams(
  body: CustomFilters,
  user: string,
): Record<string, string> {
  const {
    assignee,
    status,
    bpmnProcess,
    tenant,
    dueDateFrom,
    dueDateTo,
    followUpDateFrom,
    followUpDateTo,
    taskId,
  } = body;
  const params: Record<string, string> = {};

  if (body === undefined) {
    return params;
  }

  switch (assignee) {
    case 'all':
      break;
    case 'unassigned':
      params.assigned = 'false';
      break;
    case 'me':
      params.assigned = 'true';
      params.assignee = user;
      break;
    case 'user-and-group':
      if (body.assignedTo !== undefined) {
        params.assigned = 'true';
        params.assignee = body.assignedTo;
      }

      if (body.candidateGroup !== undefined) {
        params.candidateGroup = body.candidateGroup;
      }

      break;
  }

  if (status !== 'all') {
    params.state = status === 'open' ? 'CREATED' : 'COMPLETED';
  }

  if (![undefined, 'all'].includes(bpmnProcess)) {
    params.processDefinitionKey = bpmnProcess!;
  }

  if (tenant !== undefined) {
    params.tenantIds = JSON.stringify([tenant]);
  }

  if (dueDateFrom !== undefined) {
    params.dueDateFrom = formatRFC3339(dueDateFrom);
  }

  if (dueDateTo !== undefined) {
    params.dueDateTo = formatRFC3339(dueDateTo);
  }

  if (followUpDateFrom !== undefined) {
    params.followUpDateFrom = formatRFC3339(followUpDateFrom);
  }

  if (followUpDateTo !== undefined) {
    params.followUpDateTo = formatRFC3339(followUpDateTo);
  }

  if (taskId !== undefined) {
    params.taskDefinitionId = taskId;
  }

  return params;
}

export {prepareCustomFiltersParams};
