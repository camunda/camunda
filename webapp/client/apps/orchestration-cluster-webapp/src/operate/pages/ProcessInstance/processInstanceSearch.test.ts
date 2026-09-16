/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	getDefaultProcessInstanceTab,
	getProcessInstanceTabPath,
	hasProcessInstanceSelection,
	processInstanceSearchSchema,
	type ProcessInstanceSearch,
	type ProcessInstanceSelection,
} from './processInstanceSearch';

describe('hasProcessInstanceSelection', () => {
	it('should validate selection values while preserving other query context', () => {
		const parsed: ProcessInstanceSearch = processInstanceSearchSchema.parse({
			elementId: 'task',
			elementInstanceKey: 123,
			customHint: 'focus',
		});
		expect(parsed).toEqual({elementId: 'task', elementInstanceKey: '123', customHint: 'focus'});
		expect(parsed.customHint).toBe('focus');
	});

	it('should treat empty-string selections as missing after parsing', () => {
		const parsed: ProcessInstanceSearch = processInstanceSearchSchema.parse({
			elementId: '',
			elementInstanceKey: '',
		});
		const selection: ProcessInstanceSelection = parsed;
		expect(hasProcessInstanceSelection(selection)).toBe(false);
	});

	it('should return true when an element id is present', () => {
		expect(hasProcessInstanceSelection({elementId: 'task'})).toBe(true);
	});

	it('should return true when an element instance key is present', () => {
		expect(hasProcessInstanceSelection({elementInstanceKey: '123'})).toBe(true);
	});

	it('should return false when there is no selection', () => {
		expect(hasProcessInstanceSelection({})).toBe(false);
		expect(hasProcessInstanceSelection({elementId: '', elementInstanceKey: ''})).toBe(false);
	});
});

describe('getDefaultProcessInstanceTab', () => {
	it('should prefer incidents when the process instance has incidents', () => {
		expect(getDefaultProcessInstanceTab({hasIncident: true}, {})).toBe('incidents');
	});

	it('should return details when there is an element selection', () => {
		expect(getDefaultProcessInstanceTab({hasIncident: false}, {elementId: 'task'})).toBe('details');
	});

	it('should return details when process-level wait state is present', () => {
		expect(getDefaultProcessInstanceTab({hasIncident: false}, {}, {isProcessLevelWaiting: true})).toBe('details');
	});

	it('should return variables when there are no incidents and no selection', () => {
		expect(getDefaultProcessInstanceTab({hasIncident: false}, {})).toBe('variables');
	});
});

describe('getProcessInstanceTabPath', () => {
	it('should map details tab to the details route', () => {
		expect(getProcessInstanceTabPath('details')).toBe('/operate/processes/$processInstanceId/details');
	});

	it('should map incidents tab to the incidents route', () => {
		expect(getProcessInstanceTabPath('incidents')).toBe('/operate/processes/$processInstanceId/incidents');
	});

	it('should map variables tab to the variables route', () => {
		expect(getProcessInstanceTabPath('variables')).toBe('/operate/processes/$processInstanceId/variables');
	});
});
