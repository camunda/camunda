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
			states: ['EVALUATED'],
			version: undefined,
		},
		{
			name: 'failed-only bookmark',
			search: {failed: true, decisionDefinitionId: 'invoice', decisionDefinitionVersion: 3, tenantId: 'tenant-a'},
			states: ['FAILED'],
			version: 3,
		},
		{
			name: 'no-state bookmark',
			search: {decisionDefinitionId: 'invoice', decisionDefinitionVersion: 'all', tenantId: 'tenant-a'},
			states: [],
			version: undefined,
		},
		{name: 'bare legacy URL', search: {}, states: [], version: undefined},
		{
			name: 'unified explicit selection',
			search: {evaluated: true, failed: true, decisionDefinitionId: 'invoice', decisionDefinitionVersion: 3},
			states: ['EVALUATED', 'FAILED'],
			version: 3,
		},
		{
			name: 'unified explicit false selection',
			search: {evaluated: false, failed: true, decisionDefinitionId: 'invoice'},
			states: ['FAILED'],
			version: undefined,
		},
	] as const)('should preserve $name', ({search, states, version}) => {
		const parsed = validateDecisionsSearch(search);

		expect(parsed.decisionDefinitionVersion).toBe(version);
		if (states.length === 0) {
			expect(mapDecisionInstancesFilter(parsed)).toBeUndefined();
		} else {
			expect(mapDecisionInstancesFilter(parsed)).toMatchObject({
				state: {$in: states},
				decisionDefinitionId: 'invoice',
				decisionDefinitionVersion: version,
				...('tenantId' in search ? {tenantId: 'tenant-a'} : {}),
			});
		}
		if ('tenantId' in search) {
			expect(parsed.tenantId).toBe('tenant-a');
		}
	});

	it.for([
		{evaluated: 'not-a-boolean'},
		{failed: 'not-a-boolean'},
		{decisionDefinitionVersion: 'not-a-version'},
		{decisionDefinitionVersion: 0},
	])('should reject malformed search instead of broadening the filter', (search) => {
		expect(() => validateDecisionsSearch(search)).toThrow();
	});
});
