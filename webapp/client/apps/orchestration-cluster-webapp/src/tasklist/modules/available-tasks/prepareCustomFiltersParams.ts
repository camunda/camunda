/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatRFC3339} from 'date-fns';
import type {CustomFilters} from '#/tasklist/modules/available-tasks/customFiltersSchema';
import type {CustomFilterSearchParams} from './searchSchema';

function prepareCustomFiltersParams(filters: CustomFilters, user: string): CustomFilterSearchParams {
	const {assignee, status, bpmnProcess, tenant, dueDateFrom, dueDateTo, followUpDateFrom, followUpDateTo, taskId} =
		filters;
	const params: CustomFilterSearchParams = {};

	switch (assignee) {
		case 'all': {
			break;
		}

		case 'unassigned': {
			params.assigned = 'false';
			break;
		}

		case 'me': {
			params.assigned = 'true';
			params.assignee = user;
			break;
		}

		case 'user-and-group': {
			if (filters.assignedTo !== undefined) {
				params.assigned = 'true';
				params.assignee = filters.assignedTo;
			}

			if (filters.candidateGroup !== undefined) {
				params.candidateGroup = filters.candidateGroup;
			}

			break;
		}
	}

	if (status !== 'all') {
		params.state = status === 'open' ? 'CREATED' : 'COMPLETED';
	}

	if (bpmnProcess !== undefined && bpmnProcess !== 'all') {
		params.processDefinitionKey = bpmnProcess;
	}

	if (tenant !== undefined && tenant !== '') {
		params.tenantId = tenant;
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
		params.elementId = taskId;
	}

	return params;
}

export {prepareCustomFiltersParams};
