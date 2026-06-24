/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {tasklistIndexSearchSchema, enforceSortInvariant, type TasklistIndexSearch} from './searchSchema';

describe('tasklistIndexSearchSchema', () => {
	it('should apply defaults when no params are provided', () => {
		expect(tasklistIndexSearchSchema.parse({})).toEqual({filter: 'all-open', sortBy: 'creation'});
	});

	it('should default a missing sortBy while keeping a provided filter', () => {
		expect(tasklistIndexSearchSchema.parse({filter: 'completed'})).toEqual({
			filter: 'completed',
			sortBy: 'creation',
		});
	});

	it('should parse valid filter and sortBy values', () => {
		expect(tasklistIndexSearchSchema.parse({filter: 'completed', sortBy: 'completion'})).toEqual({
			filter: 'completed',
			sortBy: 'completion',
		});
	});

	it('should accept an arbitrary filter id (custom filter)', () => {
		expect(tasklistIndexSearchSchema.parse({filter: '1718791234567abc'}).filter).toBe('1718791234567abc');
	});

	it('should accept the literal custom filter id', () => {
		expect(tasklistIndexSearchSchema.parse({filter: 'custom'}).filter).toBe('custom');
	});

	it('should reject an invalid sortBy value', () => {
		expect(tasklistIndexSearchSchema.safeParse({sortBy: 'not-a-sort'}).success).toBe(false);
	});

	it('should parse custom filter criteria params', () => {
		const result = tasklistIndexSearchSchema.parse({
			filter: 'custom',
			state: 'CREATED',
			assignee: 'demo',
			processDefinitionKey: 'process-1',
			dueDateFrom: '2024-01-01T00:00:00.000Z',
		});

		expect(result).toMatchObject({
			filter: 'custom',
			state: 'CREATED',
			assignee: 'demo',
			processDefinitionKey: 'process-1',
			dueDateFrom: '2024-01-01T00:00:00.000Z',
		});
	});
});

describe('enforceSortInvariant', () => {
	const next = (search: TasklistIndexSearch) => search;

	it('should reset completion sort to creation when the filter is all-open', () => {
		const result = enforceSortInvariant({search: {filter: 'all-open', sortBy: 'completion'}, next});

		expect(result).toEqual({filter: 'all-open', sortBy: 'creation'});
	});

	it('should reset completion sort to creation when the filter is unassigned', () => {
		const result = enforceSortInvariant({search: {filter: 'unassigned', sortBy: 'completion'}, next});

		expect(result).toEqual({filter: 'unassigned', sortBy: 'creation'});
	});

	it('should reset completion sort to creation when the filter is a saved custom filter id', () => {
		const result = enforceSortInvariant({search: {filter: '1718791234567abc', sortBy: 'completion'}, next});

		expect(result).toEqual({filter: '1718791234567abc', sortBy: 'creation'});
	});

	it('should keep completion sort when the filter is completed', () => {
		const result = enforceSortInvariant({search: {filter: 'completed', sortBy: 'completion'}, next});

		expect(result).toEqual({filter: 'completed', sortBy: 'completion'});
	});

	it('should keep completion sort when the filter is the literal custom id', () => {
		const result = enforceSortInvariant({search: {filter: 'custom', sortBy: 'completion'}, next});

		expect(result).toEqual({filter: 'custom', sortBy: 'completion'});
	});

	it('should leave non-completion sort values untouched', () => {
		const result = enforceSortInvariant({search: {filter: 'assigned-to-me', sortBy: 'due'}, next});

		expect(result).toEqual({filter: 'assigned-to-me', sortBy: 'due'});
	});
});
