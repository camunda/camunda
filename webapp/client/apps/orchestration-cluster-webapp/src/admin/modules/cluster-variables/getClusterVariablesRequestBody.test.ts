/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {getClusterVariablesRequestBody} from './getClusterVariablesRequestBody';

describe('getClusterVariablesRequestBody', () => {
	it('should sort by name ascending and use the default page size when nothing is selected', () => {
		// given
		const search = {};

		// when
		const body = getClusterVariablesRequestBody(search);

		// then
		expect(body).toEqual({
			sort: [{field: 'name', order: 'ASC'}],
			filter: undefined,
			page: {from: 0, limit: 20},
		});
	});

	it('should filter by a trimmed, wildcard-wrapped name', () => {
		// given
		const search = {search: '  my-var '};

		// when
		const body = getClusterVariablesRequestBody(search);

		// then
		expect(body.filter).toEqual({name: {$like: '*my-var*'}});
	});

	it('should ignore a blank search term', () => {
		// given
		const search = {search: '   '};

		// when
		const body = getClusterVariablesRequestBody(search);

		// then
		expect(body.filter).toBeUndefined();
	});

	it('should translate the page and page size into an offset', () => {
		// given
		const search = {page: 3, pageSize: 50 as const, sortOrder: 'DESC' as const};

		// when
		const body = getClusterVariablesRequestBody(search);

		// then
		expect(body.page).toEqual({from: 100, limit: 50});
		expect(body.sort).toEqual([{field: 'name', order: 'DESC'}]);
	});
});
