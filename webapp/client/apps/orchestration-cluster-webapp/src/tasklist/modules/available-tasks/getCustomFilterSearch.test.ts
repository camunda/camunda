/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, afterEach} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getCustomFilterSearch} from './getCustomFilterSearch';
import {storeStateLocally} from '#/shared/browser-storage/local-storage';
import type {TasklistIndexSearch} from './searchSchema';

const BASE_SEARCH: TasklistIndexSearch = {filter: 'all-open', sortBy: 'creation'};

describe('getCustomFilterSearch', () => {
	afterEach(() => {
		localStorage.clear();
	});

	it('should return cleared criteria with the filter id when filter is not in storage', () => {
		const result = getCustomFilterSearch({currentSearch: BASE_SEARCH, filter: 'missing', username: 'demo'});

		expect(result.filter).toBe('missing');
		expect(result.state).toBeUndefined();
		expect(result.assigned).toBeUndefined();
		expect(result.assignee).toBeUndefined();
		expect(result.processDefinitionKey).toBeUndefined();
		expect(result.tenantId).toBeUndefined();
	});

	it('should build params from a stored filter', () => {
		storeStateLocally('tasklist.customFilters', {
			custom: {assignee: 'me', status: 'completed', bpmnProcess: 'process-1'},
		});

		const result = getCustomFilterSearch({currentSearch: BASE_SEARCH, filter: 'custom', username: 'demo'});

		expect(result.filter).toBe('custom');
		expect(result.assigned).toBe('true');
		expect(result.assignee).toBe('demo');
		expect(result.state).toBe('COMPLETED');
		expect(result.processDefinitionKey).toBe('process-1');
	});

	it('should downgrade completion sort to creation', () => {
		storeStateLocally('tasklist.customFilters', {
			custom: {assignee: 'all', status: 'all'},
		});

		const result = getCustomFilterSearch({
			currentSearch: {...BASE_SEARCH, sortBy: 'completion'},
			filter: 'custom',
			username: 'demo',
		});

		expect(result.sortBy).toBe('creation');
	});

	it('should preserve non-completion sort values', () => {
		storeStateLocally('tasklist.customFilters', {
			custom: {assignee: 'all', status: 'all'},
		});

		const result = getCustomFilterSearch({
			currentSearch: {...BASE_SEARCH, sortBy: 'due'},
			filter: 'custom',
			username: 'demo',
		});

		expect(result.sortBy).toBe('due');
	});

	it('should set filter to the provided id', () => {
		storeStateLocally('tasklist.customFilters', {
			'my-filter-123': {assignee: 'all', status: 'open'},
		});

		const result = getCustomFilterSearch({
			currentSearch: BASE_SEARCH,
			filter: 'my-filter-123',
			username: 'demo',
		});

		expect(result.filter).toBe('my-filter-123');
	});

	it('should clear stale criteria keys not present in the stored filter', () => {
		storeStateLocally('tasklist.customFilters', {
			custom: {assignee: 'all', status: 'open'},
		});

		const currentSearch: TasklistIndexSearch = {
			...BASE_SEARCH,
			filter: 'custom',
			processDefinitionKey: 'old-process',
			tenantId: 'old-tenant',
		};

		const result = getCustomFilterSearch({currentSearch, filter: 'custom', username: 'demo'});

		expect(result.processDefinitionKey).toBeUndefined();
		expect(result.tenantId).toBeUndefined();
		expect(result.state).toBe('CREATED');
	});

	it('should include tenantId when the stored filter has a non-empty tenant', () => {
		storeStateLocally('tasklist.customFilters', {
			custom: {assignee: 'all', status: 'all', tenant: '<default>'},
		});

		const result = getCustomFilterSearch({currentSearch: BASE_SEARCH, filter: 'custom', username: 'demo'});

		expect(result.tenantId).toBe('<default>');
	});
});
