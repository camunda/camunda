/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {formatVariablesToFormData} from './formatVariablesToFormData';

describe('formatVariablesToFormData', () => {
	it('should format valid, null, and invalid variable values', () => {
		expect(
			formatVariablesToFormData([
				{name: 'customer', value: '{"name":"John"}'},
				{name: 'empty', value: null},
				{name: 'plainText', value: 'not JSON'},
			]),
		).toEqual({
			customer: {name: 'John'},
			empty: '',
			plainText: 'not JSON',
		});
	});
});
