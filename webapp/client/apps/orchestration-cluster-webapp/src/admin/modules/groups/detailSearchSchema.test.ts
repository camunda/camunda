/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getGroupMembersRequestBody} from './detailSearchSchema';

describe('getGroupMembersRequestBody', () => {
	it('should not sort by default', () => {
		expect(getGroupMembersRequestBody({}, 'users')).toEqual({page: {from: 0, limit: 20}});
	});

	it('should sort users by username', () => {
		expect(getGroupMembersRequestBody({sortField: 'username', sortOrder: 'DESC'}, 'users').sort).toEqual([
			{field: 'username', order: 'DESC'},
		]);
	});

	it('should sort mapping rules by claim value', () => {
		expect(getGroupMembersRequestBody({sortField: 'claimValue'}, 'mappingRules').sort).toEqual([
			{field: 'claimValue', order: 'ASC'},
		]);
	});

	it('should drop a sort field that belongs to another tab', () => {
		expect(getGroupMembersRequestBody({sortField: 'name'}, 'clients')).toEqual({page: {from: 0, limit: 20}});
	});
});
