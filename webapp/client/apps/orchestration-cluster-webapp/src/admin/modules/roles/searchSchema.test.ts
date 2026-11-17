/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getRolesRequestBody, rolesSearchSchema} from './searchSchema';

describe('getRolesRequestBody', () => {
	it('should sort by role ID on the first page by default', () => {
		expect(getRolesRequestBody({})).toEqual({
			sort: [{field: 'roleId', order: 'ASC'}],
			filter: {},
			page: {from: 0, limit: 20},
		});
	});

	it('should filter by a contains match on the role ID', () => {
		expect(getRolesRequestBody({search: ' ops '}).filter).toEqual({roleId: {$like: '*ops*'}});
	});

	it('should match wildcard characters in the search term literally', () => {
		expect(getRolesRequestBody({search: 'a*b?c\\d'}).filter).toEqual({roleId: {$like: '*a\\*b\\?c\\\\d*'}});
	});

	it('should ignore a blank search term', () => {
		expect(getRolesRequestBody({search: '   '}).filter).toEqual({});
	});

	it('should offset by the requested page and size', () => {
		expect(getRolesRequestBody({page: 3, pageSize: 50}).page).toEqual({from: 100, limit: 50});
	});

	it('should apply the requested sorting', () => {
		expect(getRolesRequestBody({sortField: 'name', sortOrder: 'DESC'}).sort).toEqual([{field: 'name', order: 'DESC'}]);
	});
});

describe('rolesSearchSchema', () => {
	it('should reject unsupported page sizes and sort fields', () => {
		expect(rolesSearchSchema.safeParse({pageSize: 7}).success).toBe(false);
		expect(rolesSearchSchema.safeParse({sortField: 'description'}).success).toBe(false);
	});

	it('should treat a numeric search term as a string', () => {
		expect(rolesSearchSchema.parse({search: 42}).search).toBe('42');
	});
});
