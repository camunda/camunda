/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {ForbiddenError} from '#/shared/errors';
import type {RequestError} from './request';
import {mapQueryError} from './mapQueryError';

describe('mapQueryError', () => {
	it('should return ForbiddenError for failed responses with status 403', () => {
		const error: RequestError = {
			variant: 'failed-response',
			response: new Response(null, {status: 403}),
			networkError: null,
		};

		expect(mapQueryError(error)).toBeInstanceOf(ForbiddenError);
	});

	it('should preserve other errors', () => {
		const error: RequestError = {
			variant: 'failed-response',
			response: new Response(null, {status: 500}),
			networkError: null,
		};

		expect(mapQueryError(error)).toBe(error);
	});

	it('should preserve network errors', () => {
		const error: RequestError = {
			variant: 'network-error',
			response: null,
			networkError: new Error('Failed to fetch'),
		};

		expect(mapQueryError(error)).toBe(error);
	});
});
