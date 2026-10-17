/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {addToken, cancelToken, createModificationState, moveToken, variable} from './mocks';
import {
	initialModificationState,
	modificationReducer,
	type ElementModificationPayload,
	type ModificationAction,
	type ModificationState,
} from './modificationReducer';
import {isModificationModeEnabled, isMoveAllOperation} from './modificationSelectors';

function reduce(actions: ModificationAction[], state = initialModificationState) {
	return actions.reduce(modificationReducer, state);
}

function removeVariable(
	state: ModificationState,
	[scopeId, id, operation]: [string, string, 'ADD_VARIABLE' | 'EDIT_VARIABLE'],
	source: 'variables' | 'summaryModal' = 'variables',
) {
	return modificationReducer(state, {type: 'removeVariableModification', scopeId, id, operation, source});
}

describe('modificationReducer', () => {
	it('should transition between modification mode states', () => {
		const enabled = reduce([{type: 'enableModificationMode'}]);
		const applying = modificationReducer(enabled, {type: 'startApplyingModifications'});

		expect(isModificationModeEnabled(initialModificationState)).toBe(false);
		expect(enabled.status).toBe('enabled');
		expect(isModificationModeEnabled(enabled)).toBe(true);
		expect(modificationReducer(enabled, {type: 'disableModificationMode'}).status).toBe('disabled');
		expect(applying.status).toBe('applying-modifications');
		expect(isModificationModeEnabled(applying)).toBe(false);
	});

	it('should keep staged modifications when disabled, clear everything on reset and ignore unknown actions', () => {
		const state = createModificationState([cancelToken({elementId: 'task'})], {
			status: 'moving-token',
			sourceElementIdForMoveOperation: 'task',
			sourceElementInstanceKeyForMoveOperation: '1',
			sourceElementIdForAddOperation: 'other',
			lastRemovedModification: {modification: undefined, source: 'footer'},
		});

		expect(modificationReducer(state, {type: 'disableModificationMode'}).modifications).toBe(state.modifications);
		expect(modificationReducer(state, {type: 'reset'})).toEqual(initialModificationState);
		expect(modificationReducer(state, {type: 'unknown'} as unknown as ModificationAction)).toBe(state);
	});

	it('should finish adding a token with and without a staged modification', () => {
		const adding = reduce([{type: 'enableModificationMode'}, {type: 'startAddingToken', elementId: 'task'}]);
		const {payload} = addToken({elementId: 'task', scopeId: 'scope-1'});

		expect(adding).toMatchObject({status: 'adding-token', sourceElementIdForAddOperation: 'task'});
		expect(isModificationModeEnabled(adding)).toBe(true);
		expect(modificationReducer(adding, {type: 'finishAddingToken'})).toMatchObject({
			status: 'enabled',
			sourceElementIdForAddOperation: null,
			modifications: [],
		});
		expect(modificationReducer(adding, {type: 'finishAddingToken', modification: payload}).modifications).toEqual([
			{type: 'token', payload},
		]);
	});

	it.for([
		{elementInstanceKey: undefined, expectedKey: null, isMoveAll: true},
		{elementInstanceKey: '2251799813685249', expectedKey: '2251799813685249', isMoveAll: false},
	])('should track the move source for key $elementInstanceKey', ({elementInstanceKey, expectedKey, isMoveAll}) => {
		const moving = reduce([{type: 'startMovingToken', elementId: 'task', elementInstanceKey}]);
		const {payload} = moveToken({elementId: 'task', targetElementId: 'end'});
		const finished = modificationReducer(moving, {type: 'finishMovingToken', modification: payload});

		expect(moving).toMatchObject({
			status: 'moving-token',
			sourceElementIdForMoveOperation: 'task',
			sourceElementInstanceKeyForMoveOperation: expectedKey,
		});
		expect(isMoveAllOperation(moving)).toBe(isMoveAll);
		expect(finished).toMatchObject({
			status: 'enabled',
			sourceElementIdForMoveOperation: null,
			sourceElementInstanceKeyForMoveOperation: null,
		});
		expect(finished.modifications).toEqual([{type: 'token', payload}]);
		expect(isMoveAllOperation(finished)).toBe(false);
		expect(modificationReducer(moving, {type: 'finishMovingToken'}).modifications).toEqual([]);
	});

	it('should undo the latest modification and remember it for the footer', () => {
		const first = addToken({elementId: 'task-1', scopeId: 'scope-1'});
		const second = cancelToken({elementId: 'task-2', affectedTokenCount: 3});
		const undoneOnce = reduce([
			{type: 'addModification', modification: first},
			{type: 'addModification', modification: second},
			{type: 'removeLastModification'},
		]);
		const undoneTwice = modificationReducer(undoneOnce, {type: 'removeLastModification'});

		expect(undoneOnce).toMatchObject({modifications: [first], lastRemovedModification: {modification: second}});
		expect(undoneTwice).toMatchObject({modifications: [], lastRemovedModification: {modification: first}});
		expect(modificationReducer(undoneTwice, {type: 'removeLastModification'}).lastRemovedModification).toEqual({
			modification: undefined,
			source: 'footer',
		});
	});

	it('should remove element modifications by scope for additions and by instance key otherwise', () => {
		const add = addToken({elementId: 'task-1', scopeId: 'scope-1'});
		const cancelAll = cancelToken({elementId: 'task-2', affectedTokenCount: 3});
		const cancelOne = cancelToken({elementId: 'task-2', elementInstanceKey: '1'});
		const move = moveToken({elementId: 'task-3', targetElementId: 'task-4', scopeIds: ['scope-2']});
		const state = createModificationState([add, cancelAll, cancelOne, move]);
		const remove = ({payload}: {payload: ElementModificationPayload}) =>
			modificationReducer(state, {type: 'removeElementModification', modification: payload}).modifications;

		expect(remove(addToken({elementId: 'missing', scopeId: 'scope-1'}))).toEqual(state.modifications);
		expect(remove(addToken({elementId: 'task-1', scopeId: 'other-scope'}))).toEqual(state.modifications);
		expect(remove(addToken({elementId: 'task-2', scopeId: 'scope-1'}))).toEqual(state.modifications);
		expect(remove(addToken({elementId: 'task-1', scopeId: 'scope-1'}))).toEqual([cancelAll, cancelOne, move]);
		expect(remove(cancelToken({elementId: 'task-2'}))).toEqual([add, cancelOne, move]);
		expect(remove(cancelToken({elementId: 'task-2', elementInstanceKey: '1'}))).toEqual([add, cancelAll, move]);
		expect(remove(moveToken({elementId: 'task-3', targetElementId: 'elsewhere'}))).toEqual([add, cancelAll, cancelOne]);
	});

	it('should remove every revision of the latest matching variable modification', () => {
		const added = variable({operation: 'ADD_VARIABLE', id: '1', scopeId: '1', name: 'first', newValue: '"a"'});
		const addedAgain = variable({operation: 'ADD_VARIABLE', id: '1', scopeId: '1', name: 'renamed', newValue: '"b"'});
		const edited = variable({operation: 'EDIT_VARIABLE', scopeId: '2', name: 'existing', newValue: '2', oldValue: '1'});
		const state = createModificationState([added, edited, addedAgain]);
		const withoutAdded = removeVariable(state, ['1', '1', 'ADD_VARIABLE'], 'summaryModal');
		const withoutEdited = removeVariable(withoutAdded, ['2', 'existing', 'EDIT_VARIABLE']);

		expect(removeVariable(state, ['missing', '1', 'ADD_VARIABLE'])).toBe(state);
		expect(removeVariable(state, ['1', '1', 'EDIT_VARIABLE'])).toBe(state);
		expect(withoutAdded.modifications).toEqual([edited]);
		expect(withoutAdded.lastRemovedModification).toEqual({modification: addedAgain, source: 'summaryModal'});
		expect(withoutEdited.modifications).toEqual([]);
		expect(withoutEdited.lastRemovedModification).toEqual({modification: edited, source: 'variables'});
	});

	it('should not remove a variable whose latest revision changed operation', () => {
		const state = createModificationState([
			variable({operation: 'ADD_VARIABLE', id: 'shared', scopeId: '1', name: 'a', newValue: '1'}),
			variable({operation: 'EDIT_VARIABLE', id: 'shared', scopeId: '1', name: 'a', newValue: '2'}),
		]);

		expect(removeVariable(state, ['1', 'shared', 'ADD_VARIABLE'])).toBe(state);
	});

	it('should never mutate the previous state', () => {
		const state = createModificationState([
			addToken({elementId: 'task', scopeId: 'scope'}),
			variable({operation: 'ADD_VARIABLE', id: '1', scopeId: 'scope', name: 'a', newValue: '1'}),
		]);
		const snapshot = structuredClone(state);

		reduce(
			[
				{type: 'addModification', modification: cancelToken({elementId: 'other'})},
				{type: 'removeLastModification'},
				{type: 'removeVariableModification', scopeId: 'scope', id: '1', operation: 'ADD_VARIABLE', source: 'footer'},
				{type: 'startMovingToken', elementId: 'task'},
				{type: 'finishMovingToken'},
				{type: 'reset'},
			],
			state,
		);

		expect(state).toEqual(snapshot);
	});
});
