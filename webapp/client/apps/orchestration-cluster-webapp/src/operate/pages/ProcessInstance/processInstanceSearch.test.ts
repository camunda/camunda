/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {defaultParseSearch} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {
	getDefaultProcessInstanceTab,
	getProcessInstanceTabPath,
	hasProcessInstanceSelection,
	processInstanceSearchSchema,
	validateProcessInstanceRouteSearch,
	type ProcessInstanceSearch,
	type ProcessInstanceSelection,
} from './processInstanceSearch';

describe('hasProcessInstanceSelection', () => {
	it.for(
		(['elementId', 'elementInstanceKey', 'anchorElementId'] as const).flatMap((parameter) =>
			[undefined, '', 'null', 'false', '123', 'task'].map((value) => ({parameter, value})),
		),
	)('should preserve legacy URL selection for $parameter=$value', ({parameter, value}) => {
		const searchParams = new URLSearchParams();
		if (value !== undefined) {
			searchParams.set(parameter, value);
		}
		const legacyValue = searchParams.get(parameter);
		const hasSelection = parameter !== 'anchorElementId' && Boolean(legacyValue);

		const parsed = validateProcessInstanceRouteSearch(defaultParseSearch(`?${searchParams}`));

		expect(parsed[parameter]).toBe(legacyValue ?? undefined);
		expect(hasProcessInstanceSelection(parsed)).toBe(hasSelection);
		expect(getDefaultProcessInstanceTab({hasIncident: false}, parsed)).toBe(hasSelection ? 'details' : 'variables');
		expect(getDefaultProcessInstanceTab({hasIncident: true}, parsed)).toBe('incidents');
	});

	it('should clear selection by removing URL parameters while preserving other search context', () => {
		const searchParams = new URLSearchParams({
			elementId: 'task',
			elementInstanceKey: '123',
			customHint: 'focus',
		});
		searchParams.delete('elementId');
		searchParams.delete('elementInstanceKey');

		const parsed = validateProcessInstanceRouteSearch(defaultParseSearch(`?${searchParams}`));

		expect(parsed).toEqual({customHint: 'focus'});
		expect(hasProcessInstanceSelection(parsed)).toBe(false);
		expect(getDefaultProcessInstanceTab({hasIncident: false}, parsed)).toBe('variables');
	});

	it('should validate selection values while preserving other query context', () => {
		const search = {
			elementId: 'task',
			elementInstanceKey: 123,
			customHint: 'focus',
		};
		const parsed: ProcessInstanceSearch = processInstanceSearchSchema.parse(search);
		expect(parsed).toEqual({elementId: 'task', elementInstanceKey: '123', customHint: 'focus'});
		expect(parsed.customHint).toBe('focus');
		expect(validateProcessInstanceRouteSearch(search)).toEqual(parsed);
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
