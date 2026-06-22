/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {prepareCustomFiltersParams} from './prepareCustomFiltersParams';
import type {CustomFilters} from './customFiltersSchema';

const DEFAULTS: CustomFilters = {assignee: 'all', status: 'all'};

describe('prepareCustomFiltersParams', () => {
	it('should return empty params for default all/all filters', () => {
		const result = prepareCustomFiltersParams(DEFAULTS, 'demo');

		expect(result).toEqual({});
	});

	it('should set assigned false for unassigned', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, assignee: 'unassigned'}, 'demo');

		expect(result).toEqual({assigned: 'false'});
	});

	it('should set assigned true and assignee to the current user for me', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, assignee: 'me'}, 'demo');

		expect(result).toEqual({assigned: 'true', assignee: 'demo'});
	});

	it('should set assigned and assignee from assignedTo for user-and-group', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, assignee: 'user-and-group', assignedTo: 'alice'}, 'demo');

		expect(result).toMatchObject({assigned: 'true', assignee: 'alice'});
	});

	it('should set candidateGroup for user-and-group', () => {
		const result = prepareCustomFiltersParams(
			{...DEFAULTS, assignee: 'user-and-group', candidateGroup: 'accounting'},
			'demo',
		);

		expect(result).toMatchObject({candidateGroup: 'accounting'});
	});

	it('should omit assignee and group when user-and-group sub-fields are undefined', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, assignee: 'user-and-group'}, 'demo');

		expect(result).toEqual({});
	});

	it('should map status open to state CREATED', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, status: 'open'}, 'demo');

		expect(result).toEqual({state: 'CREATED'});
	});

	it('should map status completed to state COMPLETED', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, status: 'completed'}, 'demo');

		expect(result).toEqual({state: 'COMPLETED'});
	});

	it('should omit state when status is all', () => {
		const result = prepareCustomFiltersParams(DEFAULTS, 'demo');

		expect(result).not.toHaveProperty('state');
	});

	it('should set processDefinitionKey when bpmnProcess is set', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, bpmnProcess: 'process-123'}, 'demo');

		expect(result).toEqual({processDefinitionKey: 'process-123'});
	});

	it('should omit processDefinitionKey when bpmnProcess is all', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, bpmnProcess: 'all'}, 'demo');

		expect(result).not.toHaveProperty('processDefinitionKey');
	});

	it('should omit processDefinitionKey when bpmnProcess is undefined', () => {
		const result = prepareCustomFiltersParams(DEFAULTS, 'demo');

		expect(result).not.toHaveProperty('processDefinitionKey');
	});

	it('should set tenantId when tenant is non-empty', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, tenant: '<default>'}, 'demo');

		expect(result).toEqual({tenantId: '<default>'});
	});

	it('should omit tenantId when tenant is empty string', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, tenant: ''}, 'demo');

		expect(result).not.toHaveProperty('tenantId');
	});

	it('should omit tenantId when tenant is undefined', () => {
		const result = prepareCustomFiltersParams(DEFAULTS, 'demo');

		expect(result).not.toHaveProperty('tenantId');
	});

	it('should format dueDateFrom and dueDateTo as RFC 3339', () => {
		const from = new Date('2024-01-15T10:00:00.000Z');
		const to = new Date('2024-02-15T18:30:00.000Z');
		const result = prepareCustomFiltersParams({...DEFAULTS, dueDateFrom: from, dueDateTo: to}, 'demo');

		expect(result.dueDateFrom).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
		expect(result.dueDateTo).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
	});

	it('should format followUpDateFrom and followUpDateTo as RFC 3339', () => {
		const from = new Date('2024-03-01T00:00:00.000Z');
		const to = new Date('2024-03-31T23:59:59.000Z');
		const result = prepareCustomFiltersParams({...DEFAULTS, followUpDateFrom: from, followUpDateTo: to}, 'demo');

		expect(result.followUpDateFrom).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
		expect(result.followUpDateTo).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/);
	});

	it('should map taskId to elementId', () => {
		const result = prepareCustomFiltersParams({...DEFAULTS, taskId: 'my-task-id'}, 'demo');

		expect(result).toEqual({elementId: 'my-task-id'});
	});

	it('should build a full combined param set', () => {
		const result = prepareCustomFiltersParams(
			{
				assignee: 'me',
				status: 'completed',
				bpmnProcess: 'process-1',
				tenant: '<default>',
				dueDateFrom: new Date('2024-01-01T00:00:00.000Z'),
				dueDateTo: new Date('2024-01-31T23:59:59.000Z'),
				followUpDateFrom: new Date('2024-02-01T00:00:00.000Z'),
				followUpDateTo: new Date('2024-02-28T23:59:59.000Z'),
				taskId: 'task-42',
			},
			'demo',
		);

		expect(result).toMatchObject({
			assigned: 'true',
			assignee: 'demo',
			state: 'COMPLETED',
			processDefinitionKey: 'process-1',
			tenantId: '<default>',
			elementId: 'task-42',
		});
		expect(result.dueDateFrom).toBeDefined();
		expect(result.dueDateTo).toBeDefined();
		expect(result.followUpDateFrom).toBeDefined();
		expect(result.followUpDateTo).toBeDefined();
	});
});
