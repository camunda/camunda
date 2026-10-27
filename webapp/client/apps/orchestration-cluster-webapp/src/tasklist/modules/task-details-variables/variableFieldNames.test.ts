/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {
	createNewVariableFieldName,
	createVariableFieldName,
	getNewVariablePrefix,
	getVariableFieldName,
} from './variableFieldNames';

describe('variableFieldNames', () => {
	it('should create variable field name', () => {
		expect(createVariableFieldName('someVariableName')).toBe('#someVariableName');
	});

	it('should escape dots in variable names', () => {
		expect(createVariableFieldName('some.variable.name')).toBe('#some___DOT___variable___DOT___name');
	});

	it('should create new variable field name', () => {
		expect(createNewVariableFieldName('newVariables[0]', 'name')).toBe('newVariables[0].name');
		expect(createNewVariableFieldName('newVariables[0]', 'value')).toBe('newVariables[0].value');
	});

	it('should get variable field name', () => {
		expect(getVariableFieldName('#someVariableName')).toBe('someVariableName');
	});

	it('should unescape dots in variable names', () => {
		expect(getVariableFieldName('#some___DOT___variable___DOT___name')).toBe('some.variable.name');
	});

	it('should get new variable prefix', () => {
		expect(getNewVariablePrefix('newVariables[0].name')).toBe('newVariables[0]');
		expect(getNewVariablePrefix('newVariables[0].value')).toBe('newVariables[0]');
	});
});
