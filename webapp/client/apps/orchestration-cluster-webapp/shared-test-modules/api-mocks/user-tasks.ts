/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryUserTasksResponseBody, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';

function createUserTask(overrides?: Partial<UserTask>): UserTask {
	return {
		userTaskKey: '2251799813685281',
		state: 'CREATED',
		processDefinitionVersion: 1,
		processDefinitionId: 'process',
		processName: 'My Process',
		processInstanceKey: '2251799813685280',
		rootProcessInstanceKey: null,
		processDefinitionKey: '2251799813685279',
		name: 'My Task',
		elementId: 'task-1',
		elementInstanceKey: '2251799813685282',
		tenantId: '<default>',
		assignee: null,
		candidateGroups: [],
		candidateUsers: [],
		dueDate: null,
		followUpDate: null,
		creationDate: '2024-01-01T10:00:00.000Z',
		completionDate: null,
		customHeaders: null,
		formKey: null,
		externalFormReference: null,
		tags: [],
		priority: 50,
		...overrides,
	};
}

function createQueryUserTasksResponse(overrides?: {
	items?: UserTask[];
	page?: Partial<QueryUserTasksResponseBody['page']>;
}): QueryUserTasksResponseBody {
	const items = overrides?.items ?? [];
	return {
		items,
		page: {
			totalItems: items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
			...overrides?.page,
		},
	};
}

export {createUserTask, createQueryUserTasksResponse};
