/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	getAuditLogSort,
	getNextSortSearchValue,
	taskDetailsHistorySearchDefaults,
	taskDetailsHistorySearchSchema,
} from './sortUtils';

describe('task details history sorting', () => {
	it('should show newest history entries first by default', () => {
		const search = taskDetailsHistorySearchSchema.parse({});

		expect(search).toEqual(taskDetailsHistorySearchDefaults);
		expect(getAuditLogSort(search)).toEqual({field: 'timestamp', order: 'DESC'});
	});

	it('should keep the selected history sort from the URL', () => {
		const search = taskDetailsHistorySearchSchema.parse({sort: 'operationType+ASC'});

		expect(getAuditLogSort(search)).toEqual({field: 'operationType', order: 'ASC'});
	});

	it('should recover to the default sort when the URL contains an unsupported sort', () => {
		const search = taskDetailsHistorySearchSchema.parse({sort: 'status+ASC'});

		expect(search).toEqual(taskDetailsHistorySearchDefaults);
		expect(getAuditLogSort(search)).toEqual({field: 'timestamp', order: 'DESC'});
	});

	it('should build the next sort search value when the user selects a column', () => {
		expect(getNextSortSearchValue('timestamp', 'ASC')).toBe('timestamp+DESC');
		expect(getNextSortSearchValue('timestamp', 'DESC')).toBe('timestamp+ASC');
		expect(getNextSortSearchValue('actorId')).toBe('actorId+ASC');
	});
});
