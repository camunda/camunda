/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {isRedactedVariable, redactValue, redactValues} from './redactedVariable';

describe('isRedactedVariable', () => {
	it('should be true when the variable is protected with REDACT', () => {
		expect(isRedactedVariable({metadata: {protectionModes: ['REDACT']}})).toBe(true);
	});

	it('should be false when the variable has no protection modes', () => {
		expect(isRedactedVariable({metadata: {protectionModes: []}})).toBe(false);
	});

	it('should be false when the variable has no metadata', () => {
		expect(isRedactedVariable({})).toBe(false);
		expect(isRedactedVariable({metadata: null})).toBe(false);
	});
});

describe('redactValue', () => {
	it('should replace the value of a redacted variable with a JSON-parseable redacted label', () => {
		const variable = {name: 'password', value: 'stored', isTruncated: false, metadata: {protectionModes: ['REDACT']}};

		const redacted = redactValue(variable);

		expect(redacted).toEqual({...variable, value: '"[REDACTED]"'});
		expect(JSON.parse(redacted.value)).toBe('[REDACTED]');
	});

	it('should mark a truncated redacted variable as not truncated', () => {
		const variable = {name: 'password', value: '"stor', isTruncated: true, metadata: {protectionModes: ['REDACT']}};

		expect(redactValue(variable).isTruncated).toBe(false);
	});

	it('should keep the value of an unprotected variable', () => {
		const variable = {name: 'amount', value: '100', isTruncated: false, metadata: {protectionModes: []}};

		expect(redactValue(variable)).toBe(variable);
	});
});

describe('redactValues', () => {
	it('should redact the protected items of a search result and keep the rest of it', () => {
		const result = {
			items: [
				{name: 'password', value: 'stored', isTruncated: false, metadata: {protectionModes: ['REDACT']}},
				{name: 'amount', value: '100', isTruncated: false, metadata: {protectionModes: []}},
			],
			page: {totalItems: 2},
		};

		expect(redactValues(result)).toEqual({
			items: [
				{name: 'password', value: '"[REDACTED]"', isTruncated: false, metadata: {protectionModes: ['REDACT']}},
				{name: 'amount', value: '100', isTruncated: false, metadata: {protectionModes: []}},
			],
			page: {totalItems: 2},
		});
	});
});
