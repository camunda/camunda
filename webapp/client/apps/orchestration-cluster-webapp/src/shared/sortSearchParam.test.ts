/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {z} from 'zod';
import {createSortSearchParamSchema} from './sortSearchParam';

const sortSearchParamSchema = createSortSearchParamSchema(z.enum(['name', 'startDate']));

describe('createSortSearchParamSchema', () => {
	it.for([
		{value: 'name+ASC', expected: {field: 'name', order: 'ASC'}},
		{value: 'startDate+DESC', expected: {field: 'startDate', order: 'DESC'}},
	])('should parse $value into a field and an order', ({value, expected}) => {
		expect(sortSearchParamSchema.parse(value)).toEqual(expected);
	});

	it.for([
		{scenario: 'a missing param', value: undefined},
		{scenario: 'an empty param', value: ''},
		{scenario: 'a missing order instead of defaulting to ASC', value: 'name'},
		{scenario: 'a lowercase order', value: 'name+desc'},
		{scenario: 'an unknown order', value: 'name+bogus'},
		{scenario: 'an unknown field', value: 'unknown+ASC'},
		{scenario: 'extra parts', value: 'name+ASC+extra'},
	])('should reject $scenario', ({value}) => {
		expect(sortSearchParamSchema.safeParse(value).success).toBe(false);
	});
});
