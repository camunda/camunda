/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {isRedactedVariable} from './redactedVariable';

describe('isRedactedVariable', () => {
	it('is true for a REDACT variable with a null value', () => {
		expect(isRedactedVariable({value: 'null', protectionModes: ['REDACT']})).toBe(true);
	});

	it('is false for a genuine null without protection modes', () => {
		expect(isRedactedVariable({value: 'null', protectionModes: []})).toBe(false);
		expect(isRedactedVariable({value: 'null'})).toBe(false);
	});

	it('is false for a protected variable that still has a value', () => {
		expect(isRedactedVariable({value: '{"a":null}', protectionModes: ['REDACT']})).toBe(false);
	});

	it('is false for other protection modes', () => {
		expect(isRedactedVariable({value: 'null', protectionModes: ['MASK']})).toBe(false);
	});
});
