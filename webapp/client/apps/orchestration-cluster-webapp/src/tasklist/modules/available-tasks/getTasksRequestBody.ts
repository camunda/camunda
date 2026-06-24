/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryUserTasksRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {isBuiltInFilter, type TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';

const SORT_BY_FIELD: Record<
	TasklistIndexSearch['sortBy'],
	'creationDate' | 'dueDate' | 'followUpDate' | 'completionDate' | 'priority'
> = {
	creation: 'creationDate',
	due: 'dueDate',
	'follow-up': 'followUpDate',
	completion: 'completionDate',
	priority: 'priority',
};

function getTasksRequestBody(
	search: TasklistIndexSearch,
	{currentUsername}: {currentUsername: string},
): QueryUserTasksRequestBody {
	const {
		filter,
		sortBy,
		state,
		assigned,
		assignee,
		candidateGroup,
		processDefinitionKey,
		tenantId,
		dueDateFrom,
		dueDateTo,
		followUpDateFrom,
		followUpDateTo,
		elementId,
	} = search;
	const sort: QueryUserTasksRequestBody['sort'] = [
		{
			field: SORT_BY_FIELD[sortBy],
			order: 'desc',
		},
	];

	if (isBuiltInFilter(filter)) {
		switch (filter) {
			case 'assigned-to-me': {
				return {
					filter: {
						assignee: currentUsername,
						state: 'CREATED',
					},
					sort,
				};
			}

			case 'unassigned': {
				return {
					filter: {
						state: 'CREATED',
						assignee: {$exists: false},
					},
					sort,
				};
			}

			case 'completed': {
				return {
					filter: {
						state: 'COMPLETED',
					},
					sort,
				};
			}

			case 'all-open':
			default: {
				return {
					filter: {
						state: {
							$in: ['CREATED', 'ASSIGNING', 'UPDATING', 'COMPLETING', 'CANCELING'],
						},
					},
					sort,
				};
			}
		}
	}

	const customFilter: NonNullable<QueryUserTasksRequestBody['filter']> = {};

	if (state !== undefined) {
		customFilter.state = state;
	}

	if (assigned === 'false') {
		customFilter.assignee = {$exists: false};
	} else if (assigned === 'true' && assignee !== undefined) {
		customFilter.assignee = assignee;
	}

	if (candidateGroup !== undefined) {
		customFilter.candidateGroup = candidateGroup;
	}

	if (processDefinitionKey !== undefined) {
		customFilter.processDefinitionKey = processDefinitionKey;
	}

	if (tenantId !== undefined) {
		customFilter.tenantId = tenantId;
	}

	if (elementId !== undefined) {
		customFilter.elementId = elementId;
	}

	if (dueDateFrom !== undefined && dueDateTo !== undefined) {
		customFilter.dueDate = {$gte: dueDateFrom, $lte: dueDateTo};
	}

	if (followUpDateFrom !== undefined && followUpDateTo !== undefined) {
		customFilter.followUpDate = {$gte: followUpDateFrom, $lte: followUpDateTo};
	}

	const stored = getStateLocally('tasklist.customFilters')?.[filter];

	if (stored?.variables && stored.variables.length > 0) {
		customFilter.processInstanceVariables = stored.variables.map(({name, value}) => ({
			name: name!,
			value: value!,
		}));
	}

	return {
		filter: customFilter,
		sort,
	};
}

export {getTasksRequestBody};
