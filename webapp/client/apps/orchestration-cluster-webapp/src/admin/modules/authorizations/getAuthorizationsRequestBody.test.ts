/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getAuthorizationsRequestBody} from './getAuthorizationsRequestBody';

describe('getAuthorizationsRequestBody', () => {
	it('should sort by owner ID ascending and use the default page size when nothing is selected', () => {
		// when
		const body = getAuthorizationsRequestBody({}, 'PROCESS_DEFINITION');

		// then
		expect(body).toEqual({
			sort: [{field: 'ownerId', order: 'ASC'}],
			filter: {resourceType: 'PROCESS_DEFINITION'},
			page: {from: 0, limit: 20},
		});
	});

	it('should filter by a trimmed, exact owner ID', () => {
		// given
		const search = {ownerId: '  demo '};

		// when
		const body = getAuthorizationsRequestBody(search, 'USER_TASK');

		// then
		expect(body.filter).toEqual({resourceType: 'USER_TASK', ownerId: 'demo'});
	});

	it('should ignore a blank owner ID', () => {
		// given
		const search = {ownerId: '   '};

		// when
		const body = getAuthorizationsRequestBody(search, 'USER_TASK');

		// then
		expect(body.filter).toEqual({resourceType: 'USER_TASK'});
	});

	it('should translate sorting and pagination from the search', () => {
		// given
		const search = {sortField: 'resourceId', sortOrder: 'DESC', page: 3, pageSize: 50} as const;

		// when
		const body = getAuthorizationsRequestBody(search, 'AUTHORIZATION');

		// then
		expect(body.sort).toEqual([{field: 'resourceId', order: 'DESC'}]);
		expect(body.page).toEqual({from: 100, limit: 50});
	});

	it('should fall back to the default sort when sorting USER_TASK by resource ID', () => {
		// given
		const search = {sortField: 'resourceId', sortOrder: 'DESC'} as const;

		// when
		const body = getAuthorizationsRequestBody(search, 'USER_TASK');

		// then
		expect(body.sort).toEqual([{field: 'ownerId', order: 'ASC'}]);
	});
});
