/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import type {TFunction} from 'i18next';
import {validateClusterVariableValue} from './validateClusterVariableValue';

const t = ((key: string) => key) as TFunction;

describe('validateClusterVariableValue', () => {
	it.for(['{"name":"John"}', '[1,2,3]', '"text"', '123', 'true', 'false', '  42  '])(
		'should accept a valid non-null JSON value: %s',
		(value) => {
			expect(validateClusterVariableValue(value, t)).toBeUndefined();
		},
	);

	it.for([undefined, '', '   '])('should require a value: %s', (value) => {
		expect(validateClusterVariableValue(value, t)).toBe('admin.clusterVariables.valueRequiredError');
	});

	it.for(['plain text', '{"name":}', '[1,2,]'])('should reject invalid JSON: %s', (value) => {
		expect(validateClusterVariableValue(value, t)).toBe('admin.clusterVariables.valueInvalidError');
	});

	it.for(['null', ' null '])('should reject a null value: %s', (value) => {
		expect(validateClusterVariableValue(value, t)).toBe('admin.clusterVariables.valueNullError');
	});
});
