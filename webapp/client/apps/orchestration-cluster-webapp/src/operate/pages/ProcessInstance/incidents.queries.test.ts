/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {createIncident} from '#/shared-test-modules/api-mocks/incidents';
import {hasShrunkRetainedPages, incidentsQuery} from './incidents.queries';

const {getNextPageParam} = incidentsQuery({
	processInstanceKey: '1',
	errorTypes: [],
	sort: [{field: 'creationTime', order: 'DESC'}],
	enabled: true,
	isPolling: false,
});

function incidentsPage(itemCount: number, totalItems: number, hasMoreTotalItems: boolean) {
	return {
		items: Array.from({length: itemCount}, (_, index) => createIncident({incidentKey: `${index}`})),
		page: {totalItems, hasMoreTotalItems, startCursor: null, endCursor: null},
	};
}

describe('incidentsQuery', () => {
	it('should keep paging past a capped total', () => {
		expect(getNextPageParam(incidentsPage(50, 10000, true), [], 9950, [])).toBe(10000);
	});

	it('should restart paging when a later retained page comes back short', () => {
		expect(hasShrunkRetainedPages({pages: [incidentsPage(0, 40, false)], pageParams: [50]})).toBe(true);
		expect(hasShrunkRetainedPages({pages: [incidentsPage(1, 51, false)], pageParams: [50]})).toBe(true);
		expect(hasShrunkRetainedPages({pages: [incidentsPage(50, 150, false)], pageParams: [50]})).toBe(false);
		expect(hasShrunkRetainedPages({pages: [incidentsPage(20, 20, false)], pageParams: [0]})).toBe(false);
	});

	it('should stop paging at the total or after a short page', () => {
		expect(getNextPageParam(incidentsPage(50, 100, false), [], 50, [])).toBeUndefined();
		expect(getNextPageParam(incidentsPage(20, 10000, true), [], 9950, [])).toBeUndefined();
	});
});
