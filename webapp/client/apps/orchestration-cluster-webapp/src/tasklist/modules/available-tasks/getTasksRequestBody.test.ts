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

	it('should default to the all-open request body for an unknown filter', () => {
		const result = getTasksRequestBody(
			{filter: 'something-else' as TasklistIndexSearch['filter'], sortBy: 'creation'},
			{currentUsername: 'demo'},
		);

		expect(result.filter).toEqual({
			state: {$in: ['CREATED', 'ASSIGNING', 'UPDATING', 'COMPLETING', 'CANCELING']},
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
