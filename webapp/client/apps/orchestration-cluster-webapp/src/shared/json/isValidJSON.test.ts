/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {isValidJSON} from './isValidJSON';

describe('isValidJSON', () => {
	it.for(['{"name":"John"}', '[1,2,3]', '"text"', '123', 'true', 'false', 'null'])(
		'should return true for valid JSON: %s',
		(value) => {
			expect(isValidJSON(value)).toBe(true);
		},
	);

	it.for(['', 'plain text', '{"name":}', '[1,2,]', 'undefined'])(
		'should return false for invalid JSON: %s',
		(value) => {
			expect(isValidJSON(value)).toBe(false);
		},
	);
});
