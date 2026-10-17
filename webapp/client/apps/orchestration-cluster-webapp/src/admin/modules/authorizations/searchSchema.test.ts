/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {authorizationsSearchSchema, getAvailableResourceTypes, resolveResourceType} from './searchSchema';

describe('authorizationsSearchSchema', () => {
	it('should drop an unknown resource type instead of failing', () => {
		// given
		const input = {resourceType: 'NOT_A_TYPE', page: 2};

		// when
		const result = authorizationsSearchSchema.parse(input);

		// then
		expect(result.resourceType).toBeUndefined();
		expect(result.page).toBe(2);
	});

	it('should reject an unsupported sort field', () => {
		// given
		const input = {sortField: 'permissions'};

		// when
		const result = authorizationsSearchSchema.safeParse(input);

		// then
		expect(result.success).toBe(false);
	});

	it('should coerce a numeric owner ID to a string', () => {
		// given
		const input = {ownerId: 123};

		// when
		const result = authorizationsSearchSchema.parse(input);

		// then
		expect(result.ownerId).toBe('123');
	});
});

describe('getAvailableResourceTypes', () => {
	it('should hide the tenant resource type when the tenants API is disabled', () => {
		// when
		const types = getAvailableResourceTypes(false);

		// then
		expect(types).not.toContain('TENANT');
		expect(types).toContain('AUTHORIZATION');
	});

	it('should include the tenant resource type when the tenants API is enabled', () => {
		// when
		const types = getAvailableResourceTypes(true);

		// then
		expect(types).toContain('TENANT');
	});
});

describe('resolveResourceType', () => {
	it('should keep the requested resource type when it is available', () => {
		// when
		const result = resolveResourceType('USER_TASK', getAvailableResourceTypes(false));

		// then
		expect(result).toBe('USER_TASK');
	});

	it('should fall back to the first available type when the requested one is hidden', () => {
		// given
		const available = getAvailableResourceTypes(false);

		// when
		const result = resolveResourceType('TENANT', available);

		// then
		expect(result).toBe(available[0]);
	});

	it('should fall back to the first available type when nothing is requested', () => {
		// given
		const available = getAvailableResourceTypes(true);

		// when
		const result = resolveResourceType(undefined, available);

		// then
		expect(result).toBe(available[0]);
	});
});
