/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getGroupsRequestBody, groupsSearchSchema} from './searchSchema';

describe('getGroupsRequestBody', () => {
	it('should sort by group ID on the first page by default', () => {
		expect(getGroupsRequestBody({})).toEqual({
			sort: [{field: 'groupId', order: 'asc'}],
			filter: {},
			page: {from: 0, limit: 20},
		});
	});

	it('should filter by a contains match on the group ID', () => {
		expect(getGroupsRequestBody({search: ' ops '}).filter).toEqual({groupId: {$like: '*ops*'}});
	});

	it('should ignore a blank search term', () => {
		expect(getGroupsRequestBody({search: '   '}).filter).toEqual({});
	});

	it('should offset by the requested page and size', () => {
		expect(getGroupsRequestBody({page: 3, pageSize: 50}).page).toEqual({from: 100, limit: 50});
	});

	it('should apply the requested sorting', () => {
		expect(getGroupsRequestBody({sortField: 'name', sortOrder: 'desc'}).sort).toEqual([{field: 'name', order: 'desc'}]);
	});
});

describe('groupsSearchSchema', () => {
	it('should reject unsupported page sizes and sort fields', () => {
		expect(groupsSearchSchema.safeParse({pageSize: 7}).success).toBe(false);
		expect(groupsSearchSchema.safeParse({sortField: 'description'}).success).toBe(false);
	});

	it('should treat a numeric search term as a string', () => {
		expect(groupsSearchSchema.parse({search: 42}).search).toBe('42');
	});
});
