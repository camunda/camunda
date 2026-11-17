/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {untruncateJson} from './untruncateJson';

it.for([
	'"Hello"',
	'"}{]["',
	'"\\"Dr.\\" Leo Spaceman"',
	'ab\\u0065cd',
	'20',
	'true',
	'false',
	'null',
	'[]',
	'["a", "b", "c"]',
	'[ 1, 2, 3 ]',
	'{}',
	'{"foo": "bar"}',
	'{ "foo": 2 }',
	JSON.stringify({
		s: 'Hello',
		num: 10,
		b: true,
		nul: 'null',
		o: {s: 'Hello2', num: 11},
		a: ['Hello', 10, {s: 'Hello3'}],
	}),
])('should preserve complete input: %s', (value) => {
	expect(untruncateJson(value).completed).toBe(value);
});

it.for([
	['"Hello', '"Hello"'],
	['"Hello\\', '"Hello"'],
	['"ab\\u006', '"ab"'],
	['-', '-0'],
	['12.', '12.0'],
	['12e', '12e0'],
	['12E', '12E0'],
	['12e+', '12e+0'],
	['12E-', '12E-0'],
	['tr', 'true'],
	['fal', 'false'],
	['nu', 'null'],
	['[', '[]'],
	['["a", "b"', '["a", "b"]'],
	['[1, 2', '[1, 2]'],
	['[tr', '[true]'],
	['[true, fa', '[true, false]'],
	['[nul', '[null]'],
	['[1, 2,', '[1, 2]'],
	['{', '{}'],
	['{"a": "b"', '{"a": "b"}'],
	['{"a": 1', '{"a": 1}'],
	['{"hel', '{}'],
	['{"hello": 1, "wo', '{"hello": 1}'],
	['{"hello"', '{}'],
	['{"hello": 1, "world"', '{"hello": 1}'],
	['{"hello":', '{}'],
	['{"hello": 1, "world": ', '{"hello": 1}'],
	['{"hello": "wo', '{"hello": "wo"}'],
	['{"hello": [1, 2', '{"hello": [1, 2]}'],
	['["hello\\', '["hello"]'],
	['["hello", "world\\', '["hello", "world"]'],
	['["hello", "\\"Dr.]\\" Leo Spaceman', '["hello", "\\"Dr.]\\" Leo Spaceman"]'],
	['{"hello\\', '{}'],
	['{"hello": 1, "world\\', '{"hello": 1}'],
	['{"hello\\nworld": ', '{}'],
	['{"hello": 1, "hello\\nworld', '{"hello": 1}'],
] as const)('should complete truncated input: %s', ([value, expected]) => {
	expect(untruncateJson(value).completed).toBe(expected);
});

it.for([
	['{"a": 1}', 0],
	['[1, 2, 3]', 0],
	['{', 1],
	['[', 1],
	['{"a": [', 2],
	['[{"a":', 2],
] as const)('should report synthesized collection depth for %s', ([value, depth]) => {
	expect(untruncateJson(value).collectionDepth).toBe(depth);
});
