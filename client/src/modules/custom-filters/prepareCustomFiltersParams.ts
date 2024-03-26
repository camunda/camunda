/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {customFiltersSchema} from './customFiltersSchema';
import {z} from 'zod';

function prepareCustomFiltersParams(
  body: z.infer<typeof customFiltersSchema>,
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
      params.assigned = JSON.stringify(false);
      break;
    case 'me':
      params.assigned = JSON.stringify(true);
      params.assignee = user;
      break;
    case 'user-and-group':
      params.assigned = JSON.stringify(true);

      if (body.assignedTo !== undefined) {
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
    params.dueDateFrom = dueDateFrom.toISOString();
  }

  if (dueDateTo !== undefined) {
    params.dueDateTo = dueDateTo.toISOString();
  }

  if (followUpDateFrom !== undefined) {
    params.followUpDateFrom = followUpDateFrom.toISOString();
  }

  if (followUpDateTo !== undefined) {
    params.followUpDateTo = followUpDateTo.toISOString();
  }

  if (taskId !== undefined) {
    params.taskDefinitionId = taskId;
  }

  return params;
}

export {prepareCustomFiltersParams};
