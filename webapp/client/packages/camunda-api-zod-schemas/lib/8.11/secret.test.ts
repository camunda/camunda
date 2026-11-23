/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {listSecretsRequestBodySchema, listSecretsResponseBodySchema, secretStatusSchema} from './secret';

describe('listSecretsRequestBodySchema', () => {
	it.each(secretStatusSchema.options)('should accept the %s status filter', (status) => {
		// given
		const body = {filter: {status}};

		// when
		const result = listSecretsRequestBodySchema.safeParse(body);

		// then
		expect(result.success).toBe(true);
	});

	it.each([
		{$eq: 'EXPIRED'},
		{$neq: 'ACTIVE'},
		{$in: ['DISABLED', 'EXPIRED']},
		{$notIn: ['ACTIVE']},
		{$exists: true},
		{$like: '*ACTIVE'},
	])('should accept the advanced status filter %j', (status) => {
		// given
		const body = {filter: {status}};

		// when
		const result = listSecretsRequestBodySchema.safeParse(body);

		// then
		expect(result.success).toBe(true);
	});

	it.each([{$in: ['BROKEN']}, {$gt: 'ACTIVE'}])('should reject the advanced status filter %j', (status) => {
		// given
		const body = {filter: {status}};

		// when
		const result = listSecretsRequestBodySchema.safeParse(body);

		// then
		expect(result.success).toBe(false);
	});

	it.each([{}, {filter: {}}])('should accept a request without a status: %j', (body) => {
		// when
		const result = listSecretsRequestBodySchema.safeParse(body);

		// then
		expect(result.success).toBe(true);
	});

	it('should reject an unknown status', () => {
		// given
		const body = {filter: {status: 'BROKEN'}};

		// when
		const result = listSecretsRequestBodySchema.safeParse(body);

		// then
		expect(result.success).toBe(false);
	});

	it('should reject an unknown filter property, as the cluster does', () => {
		// given
		const body = {filter: {name: 'camunda.secrets.a'}};

		// when
		const result = listSecretsRequestBodySchema.safeParse(body);

		// then
		expect(result.success).toBe(false);
	});
});

describe('listSecretsResponseBodySchema', () => {
	it('should accept the listed references', () => {
		// given
		const body = {references: ['camunda.secrets.a', 'camunda.secrets.b']};

		// when
		const result = listSecretsResponseBodySchema.safeParse(body);

		// then
		expect(result.success).toBe(true);
	});
});
