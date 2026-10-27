/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {beautifyTruncatedJSON} from './beautifyTruncatedJSON';

it.for([
	['{"a":1}', '{\n\t"a": 1\n}'],
	['[1,2,3]', '[\n\t1,\n\t2,\n\t3\n]'],
	['{"a": 1', '{\n\t"a": 1\n'],
	['[1, 2', '[\n\t1,\n\t2\n'],
	['{"a": [1, 2', '{\n\t"a": [\n\t\t1,\n\t\t2\n\t'],
	['{"a": []', '{\n\t"a": []\n'],
	['{{not json at all', '{{not json at all'],
] as const)('should format %s without synthesized collection closers', ([value, expected]) => {
	expect(beautifyTruncatedJSON(value)).toBe(expected);
});
