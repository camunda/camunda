/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {modifyProcessInstanceRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {addToken, cancelToken, createModificationState, moveToken, variable} from './mocks';
import {buildModificationInstructions} from './modificationInstructions';

const PROCESS_INSTANCE_KEY = 'some-process-instance-key';

function addVariable(scopeId: string, name: string, newValue: string) {
	return variable({operation: 'ADD_VARIABLE', id: `${scopeId}-${name}`, scopeId, name, newValue});
}

function toWireFormat(state: ReturnType<typeof createModificationState>) {
	const result = buildModificationInstructions(state, PROCESS_INSTANCE_KEY);
	return result.status === 'ready' ? JSON.parse(JSON.stringify(result.instructions)) : result;
}

describe('buildModificationInstructions', () => {
	it('should serialize the latest staged token and variable modifications like legacy Operate', () => {
		const state = createModificationState([
			addToken({elementId: 'element_0', scopeId: 'random-scope-id-0'}),
			addToken({
				elementId: 'element_1',
				scopeId: 'random-scope-id-1',
				parentScopeIds: {
					'first-parent-scope': 'random-scope-id-first',
					'second-parent-scope': 'random-scope-id-second',
				},
			}),
			cancelToken({elementId: 'element_2', affectedTokenCount: 0, visibleAffectedTokenCount: 0}),
			moveToken({
				elementId: 'element_3',
				targetElementId: 'element_4',
				scopeIds: ['random-scope-id-2'],
				parentScopeIds: {
					'first-parent': 'random-scope-id-for-parent-1',
					'second-parent': 'random-scope-id-for-parent-2',
					'third-parent': 'random-scope-id-for-parent-3',
				},
			}),
			variable({
				operation: 'ADD_VARIABLE',
				id: 'random-scope-id-1-name1',
				scopeId: 'random-scope-id-1',
				name: 'draft',
				newValue: '0',
			}),
			addVariable('random-scope-id-1', 'name1', '"value1"'),
			addVariable('random-scope-id-1', 'name2', '"value2"'),
			addVariable('random-scope-id-5', 'name3', '"value3"'),
			variable({operation: 'EDIT_VARIABLE', scopeId: 'random-scope-id-5', name: 'name5', newValue: '"edited"'}),
			addVariable('random-scope-id-2', 'name6', '"value6"'),
			addVariable('random-scope-id-for-parent-1', 'name7', '"value7"'),
			addVariable('random-scope-id-for-parent-1', 'name8', '{"nested":[1,true,null]}'),
			addVariable('random-scope-id-for-parent-3', 'name9', '9'),
			addVariable('random-scope-id-first', 'name10', '"value10"'),
			cancelToken({elementId: 'element_5', elementInstanceKey: 'some_instance_key'}),
			moveToken({
				elementId: 'element_6',
				elementInstanceKey: 'some_instance_key_2',
				targetElementId: 'element_7',
				scopeIds: ['random-scope-id-3'],
				ancestorScopeType: 'sourceParent',
			}),
			addToken({
				elementId: 'element_11',
				scopeId: 'random-scope-id-11',
				ancestorElement: {instanceKey: 'some-ancestor-instance-key', elementId: 'elementid'},
			}),
		]);
		const body = toWireFormat(state);

		expect(body).toStrictEqual({
			activateInstructions: [
				{elementId: 'element_0', variableInstructions: []},
				{
					elementId: 'element_1',
					variableInstructions: [
						{variables: {name10: 'value10'}, scopeId: 'first-parent-scope'},
						{variables: {name1: 'value1', name2: 'value2'}, scopeId: 'element_1'},
					],
				},
				{
					elementId: 'element_11',
					ancestorElementInstanceKey: 'some-ancestor-instance-key',
					variableInstructions: [],
				},
			],
			moveInstructions: [
				{
					sourceElementInstruction: {sourceType: 'byId', sourceElementId: 'element_3'},
					targetElementId: 'element_4',
					variableInstructions: [
						{variables: {name7: 'value7', name8: {nested: [1, true, null]}}, scopeId: 'first-parent'},
						{variables: {name9: 9}, scopeId: 'third-parent'},
						{variables: {name6: 'value6'}, scopeId: 'element_4'},
					],
				},
				{
					sourceElementInstruction: {sourceType: 'byKey', sourceElementInstanceKey: 'some_instance_key_2'},
					targetElementId: 'element_7',
					ancestorScopeInstruction: {ancestorScopeType: 'sourceParent'},
					variableInstructions: [],
				},
			],
			terminateInstructions: [{elementId: 'element_2'}, {elementInstanceKey: 'some_instance_key'}],
		});
		expect(modifyProcessInstanceRequestBodySchema.parse(body)).toEqual(body);
	});

	it.for([
		{
			host: 'activate',
			modifications: [moveToken({elementId: 'a', targetElementId: 'b'}), addToken({elementId: 'c', scopeId: 'd'})],
		},
		{
			host: 'move',
			modifications: [
				cancelToken({elementId: 'c', elementInstanceKey: '1'}),
				moveToken({elementId: 'a', elementInstanceKey: 'key', targetElementId: 'b', scopeIds: ['s']}),
			],
		},
	])('should attach root variables only to the first $host instruction', ({host, modifications}) => {
		const state = createModificationState([
			...modifications,
			addVariable(PROCESS_INSTANCE_KEY, 'name1', '"value1"'),
			variable({operation: 'EDIT_VARIABLE', scopeId: PROCESS_INSTANCE_KEY, name: 'name2', newValue: '"value2-edited"'}),
			addVariable('random-scope-id-should-be-ignored', 'name', '"value"'),
		]);
		const snapshot = structuredClone(state);
		const body = toWireFormat(state);
		const instructions = [...body.activateInstructions, ...body.moveInstructions];

		expect(state).toEqual(snapshot);
		expect(modifyProcessInstanceRequestBodySchema.parse(body)).toEqual(body);
		expect(
			instructions.map(({variableInstructions}: {variableInstructions: unknown[]}) => variableInstructions),
		).toEqual(
			host === 'activate'
				? [[{variables: {name1: 'value1', name2: 'value2-edited'}}], []]
				: [[{variables: {name1: 'value1', name2: 'value2-edited'}}]],
		);
	});

	it('should send variables named after object prototype keys as regular variables', () => {
		const state = createModificationState([
			addToken({elementId: 'task', scopeId: 'scope'}),
			addVariable('scope', '__proto__', '{"polluted":true}'),
			addVariable('scope', 'constructor', '2'),
		]);

		expect(JSON.stringify(toWireFormat(state).activateInstructions)).toBe(
			'[{"elementId":"task","variableInstructions":[{"variables":{"__proto__":{"polluted":true},"constructor":2},"scopeId":"task"}]}]',
		);
	});

	it('should refuse root variables without an activate or move instruction to host them', () => {
		const state = createModificationState([
			cancelToken({elementId: 'element_5', elementInstanceKey: 'some_instance_key'}),
			addVariable(PROCESS_INSTANCE_KEY, 'name1', '"value1"'),
		]);

		expect(buildModificationInstructions(state, PROCESS_INSTANCE_KEY)).toEqual({status: 'missing-root-variable-host'});
	});
});
