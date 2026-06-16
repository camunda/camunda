/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryUserTasksRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import type {TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';

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
	{filter, sortBy}: Pick<TasklistIndexSearch, 'filter' | 'sortBy'>,
	{currentUsername}: {currentUsername?: string},
): QueryUserTasksRequestBody {
	const sort: QueryUserTasksRequestBody['sort'] = [
		{
			field: SORT_BY_FIELD[sortBy],
			order: 'desc',
		},
	];

	switch (filter) {
		case 'assigned-to-me':
			return {
				filter: {
					assignee: currentUsername!,
					state: 'CREATED',
				},
				sort,
			};
		case 'unassigned':
			return {
				filter: {
					state: 'CREATED',
					assignee: {$exists: false},
				},
				sort,
			};
		case 'completed':
			return {
				filter: {
					state: 'COMPLETED',
				},
				sort,
			};
		case 'all-open':
		default:
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

export {getTasksRequestBody};
