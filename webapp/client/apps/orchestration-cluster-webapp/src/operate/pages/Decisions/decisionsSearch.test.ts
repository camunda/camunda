/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mapDecisionInstancesFilter} from './decisionsFilter';
import {validateDecisionsSearch} from './decisionsSearch';

describe('validateDecisionsSearch', () => {
	it.for([
		{
			name: 'evaluated-only bookmark',
			search: {
				evaluated: true,
				decisionDefinitionId: 'invoice',
				decisionDefinitionVersion: 'all',
				tenantId: 'tenant-a',
			},
			version: undefined,
			tenantId: 'tenant-a',
			filter: {state: {$in: ['EVALUATED']}, decisionDefinitionId: 'invoice', tenantId: 'tenant-a'},
		},
		{
			name: 'failed-only bookmark',
			search: {failed: true, decisionDefinitionId: 'invoice', decisionDefinitionVersion: 3, tenantId: 'tenant-a'},
			version: 3,
			tenantId: 'tenant-a',
			filter: {
				state: {$in: ['FAILED']},
				decisionDefinitionId: 'invoice',
				decisionDefinitionVersion: 3,
				tenantId: 'tenant-a',
			},
		},
		{
			name: 'no-state bookmark',
			search: {decisionDefinitionId: 'invoice', decisionDefinitionVersion: 'all', tenantId: 'tenant-a'},
			version: undefined,
			tenantId: 'tenant-a',
			filter: undefined,
		},
		{name: 'bare legacy URL', search: {}, version: undefined, tenantId: undefined, filter: undefined},
		{
			name: 'unified explicit selection',
			search: {evaluated: true, failed: true, decisionDefinitionId: 'invoice', decisionDefinitionVersion: 3},
			version: 3,
			tenantId: undefined,
			filter: {state: {$in: ['EVALUATED', 'FAILED']}, decisionDefinitionId: 'invoice', decisionDefinitionVersion: 3},
		},
		{
			name: 'unified explicit false selection',
			search: {evaluated: false, failed: true, decisionDefinitionId: 'invoice'},
			version: undefined,
			tenantId: undefined,
			filter: {state: {$in: ['FAILED']}, decisionDefinitionId: 'invoice'},
		},
	] as const)('should preserve $name', ({search, version, tenantId, filter}) => {
		const parsed = validateDecisionsSearch(search);

		expect(parsed.decisionDefinitionVersion).toBe(version);
		expect(parsed.tenantId).toBe(tenantId);
		expect(mapDecisionInstancesFilter(parsed)).toEqual(filter);
	});

	it.for([
		{search: {evaluated: 'not-a-boolean', failed: true}, expected: {evaluated: false, failed: true}},
		{search: {failed: 'not-a-boolean', evaluated: true}, expected: {failed: false, evaluated: true}},
		{search: {decisionDefinitionVersion: 'not-a-version'}, expected: {decisionDefinitionVersion: undefined}},
		{search: {decisionDefinitionVersion: 0}, expected: {decisionDefinitionVersion: undefined}},
		{search: {businessId: 12345}, expected: {businessId: '12345'}},
	])('should ignore a malformed value instead of failing the route for $search', ({search, expected}) => {
		const parsed = validateDecisionsSearch({decisionDefinitionId: 'invoice', ...search});

		expect(parsed).toMatchObject({decisionDefinitionId: 'invoice', ...expected});
	});
});
