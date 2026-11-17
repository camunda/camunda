/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {operationsLogSearchSchema} from './searchSchema';

describe('operationsLogSearchSchema', () => {
	it('should accept ISO timestamps produced by the date range picker', () => {
		// given
		const search = {timestampFrom: '2026-01-01T00:00:00.000Z', timestampTo: '2026-01-31T00:00:00.000Z'};

		// when
		const parsed = operationsLogSearchSchema.parse(search);

		// then
		expect(parsed.timestampFrom).toBe('2026-01-01T00:00:00.000Z');
		expect(parsed.timestampTo).toBe('2026-01-31T00:00:00.000Z');
	});

	it('should drop timestamp bounds that are not valid ISO dates instead of passing them through', () => {
		// given
		const search = {timestampFrom: 'not-a-date', timestampTo: 'also-invalid'};

		// when
		const parsed = operationsLogSearchSchema.parse(search);

		// then
		expect(parsed.timestampFrom).toBeUndefined();
		expect(parsed.timestampTo).toBeUndefined();
	});
});
