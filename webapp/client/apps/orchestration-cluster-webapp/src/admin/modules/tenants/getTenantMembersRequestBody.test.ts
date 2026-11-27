/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {
	CLIENTS_SORT_FIELDS,
	MAPPING_RULES_SORT_FIELDS,
	ROLES_SORT_FIELDS,
	getTenantMembersRequestBody,
} from './getTenantMembersRequestBody';

describe('getTenantMembersRequestBody', () => {
	it('should sort by the tab ID field ascending by default', () => {
		expect(getTenantMembersRequestBody({}, ROLES_SORT_FIELDS)).toEqual({
			sort: [{field: 'roleId', order: 'ASC'}],
			page: {from: 0, limit: 20},
		});
	});

	it('should sort tenant roles by name', () => {
		expect(getTenantMembersRequestBody({sortField: 'name', sortOrder: 'DESC'}, ROLES_SORT_FIELDS).sort).toEqual([
			{field: 'name', order: 'DESC'},
		]);
	});

	it.for(['name', 'claimName', 'claimValue'] as const)('should sort tenant mapping rules by %s', (sortField) => {
		expect(getTenantMembersRequestBody({sortField}, MAPPING_RULES_SORT_FIELDS).sort).toEqual([
			{field: sortField, order: 'ASC'},
		]);
	});

	it('should fall back to the ID field when the tab cannot sort by the requested field', () => {
		expect(getTenantMembersRequestBody({sortField: 'claimName', sortOrder: 'DESC'}, CLIENTS_SORT_FIELDS).sort).toEqual([
			{field: 'clientId', order: 'DESC'},
		]);
	});

	it('should offset the page by the page size', () => {
		expect(getTenantMembersRequestBody({page: 3, pageSize: 50}, ROLES_SORT_FIELDS).page).toEqual({
			from: 100,
			limit: 50,
		});
	});
});
