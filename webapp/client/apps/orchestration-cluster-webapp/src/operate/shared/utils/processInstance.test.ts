/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {getProcessDefinitionName, isInstanceRunning} from './processInstance';

describe('getProcessDefinitionName', () => {
	it('should read names from process definitions and process instances', () => {
		expect(getProcessDefinitionName(createProcessDefinition({name: 'Definition name'}))).toBe('Definition name');
		expect(getProcessDefinitionName(createProcessInstance({processDefinitionName: 'Instance name'}))).toBe(
			'Instance name',
		);
	});

	it('should fall back to process definition ids', () => {
		expect(getProcessDefinitionName(createProcessDefinition({name: null, processDefinitionId: 'definition-id'}))).toBe(
			'definition-id',
		);
	});
});

describe('isInstanceRunning', () => {
	it.for(['COMPLETED', 'TERMINATED'] as const)('should treat a %s instance with an incident as running', (state) => {
		expect(isInstanceRunning(createProcessInstance({state, hasIncident: true}))).toBe(true);
	});
});
