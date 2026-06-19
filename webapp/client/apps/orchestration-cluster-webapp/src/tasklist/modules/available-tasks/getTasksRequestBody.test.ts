/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getTasksRequestBody} from './getTasksRequestBody';
import type {TasklistIndexSearch} from './searchSchema';

describe('getTasksRequestBody', () => {
	it('should build the request body for the all-open filter', () => {
		const result = getTasksRequestBody({filter: 'all-open', sortBy: 'creation'}, {currentUsername: 'demo'});

		expect(result).toEqual({
			filter: {
				state: {$in: ['CREATED', 'ASSIGNING', 'UPDATING', 'COMPLETING', 'CANCELING']},
			},
			sort: [{field: 'creationDate', order: 'desc'}],
		});
	});

	it('should build an empty custom filter request body for an unknown filter id', () => {
		const result = getTasksRequestBody(
			{filter: 'something-else' as TasklistIndexSearch['filter'], sortBy: 'creation'},
			{currentUsername: 'demo'},
		);

		expect(result).toEqual({
			filter: {},
			sort: [{field: 'creationDate', order: 'desc'}],
		});
	});

	it('should build the request body for the assigned-to-me filter using the current username', () => {
		const result = getTasksRequestBody({filter: 'assigned-to-me', sortBy: 'creation'}, {currentUsername: 'demo'});

		expect(result).toEqual({
			filter: {assignee: 'demo', state: 'CREATED'},
			sort: [{field: 'creationDate', order: 'desc'}],
		});
	});

	it('should build the request body for the unassigned filter', () => {
		const result = getTasksRequestBody({filter: 'unassigned', sortBy: 'creation'}, {currentUsername: 'demo'});

		expect(result).toEqual({
			filter: {state: 'CREATED', assignee: {$exists: false}},
			sort: [{field: 'creationDate', order: 'desc'}],
		});
	});

	it('should build the request body for the completed filter', () => {
		const result = getTasksRequestBody({filter: 'completed', sortBy: 'completion'}, {currentUsername: 'demo'});

		expect(result).toEqual({
			filter: {state: 'COMPLETED'},
			sort: [{field: 'completionDate', order: 'desc'}],
		});
	});

	it('should build the request body for a custom filter from URL criteria params', () => {
		const result = getTasksRequestBody(
			{
				filter: 'custom',
				sortBy: 'creation',
				state: 'CREATED',
				assigned: 'true',
				assignee: 'demo',
				candidateGroup: 'group-1',
				processDefinitionKey: 'process-1',
				tenantId: '<default>',
				elementId: 'task-1',
				dueDateFrom: '2024-01-01T00:00:00.000Z',
				dueDateTo: '2024-01-31T00:00:00.000Z',
				followUpDateFrom: '2024-02-01T00:00:00.000Z',
				followUpDateTo: '2024-02-28T00:00:00.000Z',
			},
			{currentUsername: 'demo'},
		);

		expect(result).toEqual({
			filter: {
				state: 'CREATED',
				assignee: 'demo',
				candidateGroup: 'group-1',
				processDefinitionKey: 'process-1',
				tenantId: '<default>',
				elementId: 'task-1',
				dueDate: {$gte: '2024-01-01T00:00:00.000Z', $lte: '2024-01-31T00:00:00.000Z'},
				followUpDate: {$gte: '2024-02-01T00:00:00.000Z', $lte: '2024-02-28T00:00:00.000Z'},
			},
			sort: [{field: 'creationDate', order: 'desc'}],
		});
	});

	it('should set assignee to $exists:false when assigned is false', () => {
		const result = getTasksRequestBody(
			{filter: 'custom', sortBy: 'creation', assigned: 'false'},
			{currentUsername: 'demo'},
		);

		expect(result.filter).toEqual({assignee: {$exists: false}});
	});

	it('should omit date range when only one of from/to is provided', () => {
		const result = getTasksRequestBody(
			{filter: 'custom', sortBy: 'creation', dueDateFrom: '2024-01-01T00:00:00.000Z'},
			{currentUsername: 'demo'},
		);

		expect(result.filter).not.toHaveProperty('dueDate');
	});

	it.for([
		['creation', 'creationDate'],
		['due', 'dueDate'],
		['follow-up', 'followUpDate'],
		['completion', 'completionDate'],
		['priority', 'priority'],
	] satisfies ReadonlyArray<[TasklistIndexSearch['sortBy'], string]>)(
		'should map sortBy "%s" to the "%s" sort field',
		([sortBy, field]) => {
			const result = getTasksRequestBody({filter: 'all-open', sortBy}, {currentUsername: 'demo'});

			expect(result.sort).toEqual([{field, order: 'desc'}]);
		},
	);

	it('should not set a page on the request body', () => {
		const result = getTasksRequestBody({filter: 'all-open', sortBy: 'creation'}, {currentUsername: 'demo'});

		expect(result).not.toHaveProperty('page');
	});
});
