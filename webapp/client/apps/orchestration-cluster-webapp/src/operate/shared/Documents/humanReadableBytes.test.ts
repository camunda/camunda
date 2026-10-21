/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {toHumanReadableBytes} from './humanReadableBytes';

it.for([
	[0, '0 B'],
	[Number.NaN, 'N/A'],
	[Number.POSITIVE_INFINITY, 'N/A'],
	[1, '1 B'],
	[512, '512 B'],
	[1024, '1 KiB'],
	[1536, '1.5 KiB'],
	[109748, '107.18 KiB'],
	[1048576, '1 MiB'],
	[1073741824, '1 GiB'],
	[1099511627776, '1024 GiB'],
] as const)('should format %s bytes as %s', ([bytes, expected]) => {
	expect(toHumanReadableBytes(bytes)).toBe(expected);
});
