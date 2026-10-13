/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getTenantsRequestBody} from './getTenantsRequestBody';

describe('getTenantsRequestBody', () => {
	it('should sort by tenant ID ascending and request the first page by default', () => {
		// given
		const search = {};

		// when
		const body = getTenantsRequestBody(search);

		// then
		expect(body).toEqual({
			sort: [{field: 'tenantId', order: 'ASC'}],
			filter: undefined,
			page: {from: 0, limit: 20},
		});
	});

	it('should filter by the trimmed search term', () => {
		// given
		const search = {search: '  tenant-a  '};

		// when
		const body = getTenantsRequestBody(search);

		// then
		expect(body.filter).toEqual({tenantId: 'tenant-a'});
	});

	it('should ignore a blank search term', () => {
		// given
		const search = {search: '   '};

		// when
		const body = getTenantsRequestBody(search);

		// then
		expect(body.filter).toBeUndefined();
	});

	it('should apply the requested sort and offset the page by the page size', () => {
		// given
		const search = {sortField: 'name', sortOrder: 'DESC', page: 3, pageSize: 50} as const;

		// when
		const body = getTenantsRequestBody(search);

		// then
		expect(body.sort).toEqual([{field: 'name', order: 'DESC'}]);
		expect(body.page).toEqual({from: 100, limit: 50});
	});
});
