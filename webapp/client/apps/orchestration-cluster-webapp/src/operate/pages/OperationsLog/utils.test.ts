/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {formatToISO} from './utils';

describe('formatToISO', () => {
	it('should return undefined for an undefined input', () => {
		expect(formatToISO(undefined)).toBeUndefined();
	});

	it('should return undefined for an empty string', () => {
		expect(formatToISO('')).toBeUndefined();
	});

	it('should return undefined for an unparseable date string instead of throwing', () => {
		expect(formatToISO('not-a-date')).toBeUndefined();
	});

	it('should convert a valid date string to an ISO timestamp', () => {
		expect(formatToISO('2024-01-01')).toBe('2024-01-01T00:00:00.000Z');
	});
});
