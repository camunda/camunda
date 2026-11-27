/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getRoleMembersRequestBody} from './detailSearchSchema';

describe('getRoleMembersRequestBody', () => {
	it('should request the first page without a filter by default', () => {
		expect(getRoleMembersRequestBody({}, 'users')).toEqual({page: {from: 0, limit: 20}});
	});

	it('should filter users by a contains match on the username', () => {
		expect(getRoleMembersRequestBody({search: ' jane '}, 'users').filter).toEqual({username: {$like: '*jane*'}});
	});

	it('should match wildcard characters in the search term literally', () => {
		expect(getRoleMembersRequestBody({search: 'a*b?c\\d'}, 'users').filter).toEqual({
			username: {$like: '*a\\*b\\?c\\\\d*'},
		});
	});

	it('should ignore a blank search term', () => {
		expect(getRoleMembersRequestBody({search: '   '}, 'users')).toEqual({page: {from: 0, limit: 20}});
	});

	it.for(['groups', 'mappingRules', 'clients'] as const)('should not filter the %s tab by the search term', (kind) => {
		expect(getRoleMembersRequestBody({search: 'jane'}, kind)).toEqual({page: {from: 0, limit: 20}});
	});

	it('should offset by the requested page and size', () => {
		expect(getRoleMembersRequestBody({page: 3, pageSize: 50}, 'users').page).toEqual({from: 100, limit: 50});
	});

	it('should sort groups by group ID without applying the username filter', () => {
		expect(getRoleMembersRequestBody({sortField: 'groupId', sortOrder: 'DESC', search: 'jane'}, 'groups')).toEqual({
			page: {from: 0, limit: 20},
			sort: [{field: 'groupId', order: 'DESC'}],
		});
	});

	it('should combine the username filter with the sort', () => {
		expect(getRoleMembersRequestBody({sortField: 'username', search: 'jane'}, 'users')).toEqual({
			page: {from: 0, limit: 20},
			sort: [{field: 'username', order: 'ASC'}],
			filter: {username: {$like: '*jane*'}},
		});
	});
});
