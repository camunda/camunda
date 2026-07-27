/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {tryParseJSON} from './tryParseJSON';

describe('tryParseJSON', () => {
	it('should parse valid JSON values', () => {
		expect(tryParseJSON('{"name":"John"}')).toEqual({name: 'John'});
		expect(tryParseJSON('[1,2,3]')).toEqual([1, 2, 3]);
		expect(tryParseJSON('true')).toBe(true);
	});

	it('should return the original value when it is not valid JSON', () => {
		expect(tryParseJSON('plain text')).toBe('plain text');
	});
});
