/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {escapeLikePattern} from './escapeLikePattern';

describe('escapeLikePattern', () => {
	it('should wrap plain input in wildcards', () => {
		// given
		const input = 'task';

		// when
		const pattern = escapeLikePattern(input);

		// then
		expect(pattern).toBe('*task*');
	});

	it.for([
		['a*b', '*a\\*b*'],
		['a?b', '*a\\?b*'],
		['a\\b', '*a\\\\b*'],
	])('should escape wildcard characters in %s', ([input, expected]) => {
		// when
		const pattern = escapeLikePattern(input!);

		// then
		expect(pattern).toBe(expected);
	});
});
