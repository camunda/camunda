/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {isTaskTimeoutError} from './taskErrorHandling';

describe('isTaskTimeoutError', () => {
	it('should return true for error with title DEADLINE_EXCEEDED', () => {
		expect(isTaskTimeoutError({title: 'DEADLINE_EXCEEDED'})).toBe(true);
	});

	it('should return true for error with title TASK_PROCESSING_TIMEOUT', () => {
		expect(isTaskTimeoutError({title: 'TASK_PROCESSING_TIMEOUT'})).toBe(true);
	});

	it('should return true for error with message containing DEADLINE_EXCEEDED', () => {
		expect(isTaskTimeoutError({message: 'Request failed: DEADLINE_EXCEEDED'})).toBe(true);
	});

	it('should return true for error with message containing TASK_PROCESSING_TIMEOUT', () => {
		expect(isTaskTimeoutError({message: 'Error: TASK_PROCESSING_TIMEOUT occurred'})).toBe(true);
	});

	it('should return false for unrelated error title', () => {
		expect(isTaskTimeoutError({title: 'NOT_FOUND'})).toBe(false);
	});

	it('should return false for unrelated error message', () => {
		expect(isTaskTimeoutError({message: 'Something went wrong'})).toBe(false);
	});

	it('should return false for null', () => {
		expect(isTaskTimeoutError(null)).toBe(false);
	});

	it('should return false for undefined', () => {
		expect(isTaskTimeoutError(undefined)).toBe(false);
	});

	it('should return false for empty object', () => {
		expect(isTaskTimeoutError({})).toBe(false);
	});

	it('should return false for non-object value', () => {
		expect(isTaskTimeoutError('DEADLINE_EXCEEDED')).toBe(false);
	});
});
