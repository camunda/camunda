/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getAuditLogsRequestBody} from './getAuditLogsRequestBody';

describe('getAuditLogsRequestBody', () => {
	it('should request successful task history entries', () => {
		const result = getAuditLogsRequestBody({field: 'timestamp', order: 'desc'});

		expect(result.filter).toEqual({result: 'SUCCESS'});
	});

	it('should request the first page of task history entries', () => {
		const result = getAuditLogsRequestBody({field: 'timestamp', order: 'desc'});

		expect(result.page).toEqual({from: 0, limit: 50});
	});

	it('should request task history entries in the selected order', () => {
		const result = getAuditLogsRequestBody({field: 'actorId', order: 'asc'});

		expect(result.sort).toEqual([{field: 'actorId', order: 'asc'}]);
	});
});
