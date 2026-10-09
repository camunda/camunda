/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {truncate} from './truncate';

describe('truncate', () => {
	it.for([
		{description: 'a short value unchanged', value: 'status', expected: 'status'},
		{description: 'a value of exactly 50 characters unchanged', value: 'a'.repeat(50), expected: 'a'.repeat(50)},
		{description: 'a longer value cut to 47 characters', value: 'a'.repeat(51), expected: `${'a'.repeat(47)}...`},
		{
			description: 'an emoji at the cutoff whole',
			value: `${'a'.repeat(46)}😀${'b'.repeat(10)}`,
			expected: `${'a'.repeat(46)}😀...`,
		},
	])('should keep $description', ({value, expected}) => {
		expect(truncate(value)).toBe(expected);
	});
});
