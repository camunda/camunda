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
	addToken,
	cancelToken,
	createModificationBusinessObjects,
	createModificationState,
	moveToken,
	variable,
} from './mocks';
import type {Modification} from './modificationReducer';
import {
	getElementModifications,
	getLastModification,
	getLastVariableModification,
	getModificationsByElement,
	getScopeMapForModification,
	getVariableModifications,
	hasOrphanedVariableModifications,
	hasPendingAddOrMoveModification,
	hasPendingCancelOrMoveModification,
	willAllElementsBeCanceled,
} from './modificationSelectors';

const PROCESS_INSTANCE_KEY = '2251799813685249';

function counts(overrides: Partial<ReturnType<typeof getModificationsByElement>[string]> = {}) {
	const empty = {newTokens: 0, cancelledTokens: 0, cancelledChildTokens: 0, visibleCancelledTokens: 0};
	return {...empty, areAllTokensCanceled: false, ...overrides};
}

function addVariable(scopeId: string) {
	return variable({operation: 'ADD_VARIABLE', id: scopeId, scopeId, name: scopeId, newValue: '1'});
}

describe('modificationSelectors', () => {
	it('should expose the latest modification and token payloads in staging order', () => {
		const add = addToken({elementId: 'task-1', scopeId: 'scope-1'});
		const edit = variable({operation: 'EDIT_VARIABLE', scopeId: 'scope-1', name: 'a', newValue: '1'});
		const cancel = cancelToken({elementId: 'task-2'});
		const state = createModificationState([add, edit, cancel]);

		expect(getLastModification(state)).toEqual(cancel);
		expect(getLastModification(createModificationState())).toBeUndefined();
		expect(getElementModifications(state)).toEqual([add.payload, cancel.payload]);
	});

	it('should keep the latest revision per scope and id at its first staged position', () => {
		const state = createModificationState([
			variable({operation: 'ADD_VARIABLE', id: '1', scopeId: 'element-1', name: 'name1', newValue: 'value1'}),
			variable({operation: 'EDIT_VARIABLE', scopeId: 'element-1', name: 'existing', newValue: '123', oldValue: '12'}),
			variable({operation: 'ADD_VARIABLE', id: '1', scopeId: 'element-1', name: 'name2', newValue: 'value3'}),
			variable({operation: 'EDIT_VARIABLE', scopeId: 'element-1', name: 'existing', newValue: '1234', oldValue: '12'}),
			variable({operation: 'ADD_VARIABLE', id: '1', scopeId: 'element-2', name: 'name2', newValue: 'value3'}),
		]);

		expect(
			getVariableModifications(state).map(({scopeId, id, name, newValue}) => [scopeId, id, name, newValue]),
		).toEqual([
			['element-1', '1', 'name2', 'value3'],
			['element-1', 'existing', 'existing', '1234'],
			['element-2', '1', 'name2', 'value3'],
		]);
		expect(getLastVariableModification(state, 'element-1', 'existing', 'EDIT_VARIABLE')).toMatchObject({
			newValue: '1234',
			oldValue: '12',
		});
		expect(getLastVariableModification(state, 'element-1', 'existing', 'ADD_VARIABLE')).toBeUndefined();
		expect(getLastVariableModification(state, null, '1', 'ADD_VARIABLE')).toBeUndefined();
	});

	it('should map new and parent scopes to their element ids', () => {
		expect(
			getScopeMapForModification(
				addToken({elementId: 'task', scopeId: 'scope-1', parentScopeIds: {sub_process: 'parent-scope'}}).payload,
			),
		).toEqual({'parent-scope': 'sub_process', 'scope-1': 'task'});
		expect(
			getScopeMapForModification(
				moveToken({
					elementId: 'source',
					targetElementId: 'target',
					scopeIds: ['scope-1', 'scope-2'],
					parentScopeIds: {sub_process: 'parent-scope'},
				}).payload,
			),
		).toEqual({'parent-scope': 'sub_process', 'scope-1': 'target', 'scope-2': 'target'});
		expect(getScopeMapForModification(cancelToken({elementId: 'task'}).payload)).toEqual({});
	});

	it('should detect variables that no staged token modification can host', () => {
		const add = addToken({elementId: 'task-1', scopeId: 'add-scope', parentScopeIds: {parent: 'parent-scope'}});
		const move = moveToken({elementId: 'task-1', targetElementId: 'task-2', scopeIds: ['move-scope']});
		const scoped = [addVariable('add-scope'), addVariable('move-scope'), addVariable('parent-scope')];
		const root = addVariable(PROCESS_INSTANCE_KEY);
		const isOrphaned = (...modifications: Modification[]) =>
			hasOrphanedVariableModifications(createModificationState(modifications), PROCESS_INSTANCE_KEY);

		expect(isOrphaned(add)).toBe(false);
		expect(isOrphaned(add, move, ...scoped)).toBe(false);
		expect(isOrphaned(add, move, ...scoped, addVariable('lonely'))).toBe(true);
		expect(isOrphaned(move, ...scoped)).toBe(true);
		expect(isOrphaned(cancelToken({elementId: 'x'}), root)).toBe(true);
		expect(isOrphaned(root, move)).toBe(false);
	});

	it('should report pending token modifications by operation, instance and element', () => {
		const cancelOne = cancelToken({elementId: 'task', elementInstanceKey: '1'});
		const state = createModificationState([
			cancelOne,
			moveToken({elementId: 'task', targetElementId: 'end', elementInstanceKey: '2'}),
		]);
		const modificationsByElement = getModificationsByElement(state, {totalRunningInstancesByElement: {task: 3}});
		const isPending = (elementId: string, elementInstanceKey?: string, byElement = modificationsByElement) =>
			hasPendingCancelOrMoveModification(state, {elementId, elementInstanceKey, modificationsByElement: byElement});

		expect(hasPendingAddOrMoveModification(createModificationState([cancelOne]))).toBe(false);
		expect(hasPendingAddOrMoveModification(state)).toBe(true);
		expect(hasPendingAddOrMoveModification(createModificationState([addToken({elementId: 't', scopeId: 's'})]))).toBe(
			true,
		);
		expect([isPending('end', '1'), isPending('end', '2'), isPending('task', '3')]).toEqual([true, true, false]);
		expect([isPending('task'), isPending('end'), isPending('task', undefined, {})]).toEqual([true, false, false]);
	});

	it('should count added, cancelled and moved tokens per element', () => {
		const businessObjects = createModificationBusinessObjects();
		const state = createModificationState([
			addToken({elementId: 'node1', scopeId: 's1', affectedTokenCount: 5, visibleAffectedTokenCount: 3}),
			cancelToken({elementId: 'node2', affectedTokenCount: 5, visibleAffectedTokenCount: 3}),
			moveToken({elementId: 'node3', targetElementId: 'node4', affectedTokenCount: 5, visibleAffectedTokenCount: 3}),
			moveToken({elementId: 'multi_instance_task', targetElementId: 'node4', affectedTokenCount: 4}),
		]);

		expect(getModificationsByElement(state, {businessObjects})).toEqual({
			node1: counts({newTokens: 5}),
			node2: counts({cancelledTokens: 5, visibleCancelledTokens: 3}),
			node3: counts({cancelledTokens: 5, visibleCancelledTokens: 3}),
			node4: counts({newTokens: 6}),
			multi_instance_task: counts({cancelledTokens: 4, visibleCancelledTokens: 1}),
		});
	});

	it('should accumulate single-instance cancellations and ignore later ones once all tokens are cancelled', () => {
		const state = createModificationState([
			cancelToken({elementId: 'task', elementInstanceKey: '1'}),
			moveToken({elementId: 'task', targetElementId: 'end', elementInstanceKey: '2'}),
			cancelToken({elementId: 'task', elementInstanceKey: '3'}),
			moveToken({elementId: 'task', targetElementId: 'other', affectedTokenCount: 2}),
		]);

		expect(getModificationsByElement(state, {totalRunningInstancesByElement: {task: 2}})).toEqual({
			task: counts({cancelledTokens: 2, visibleCancelledTokens: 2, areAllTokensCanceled: true}),
			end: counts({newTokens: 1}),
		});
		expect(getModificationsByElement(state, {totalRunningInstancesByElement: {task: 4}}).task).toEqual(
			counts({cancelledTokens: 2, visibleCancelledTokens: 1}),
		);
	});

	it('should cancel child tokens by element id when a sub-process is fully cancelled', () => {
		const businessObjects = createModificationBusinessObjects();
		const state = createModificationState([
			cancelToken({elementId: 'user_task', elementInstanceKey: '9'}),
			cancelToken({elementId: 'parent_sub_process', affectedTokenCount: 1, visibleAffectedTokenCount: 1}),
		]);

		expect(
			getModificationsByElement(state, {
				businessObjects,
				totalRunningInstancesByElement: {parent_sub_process: 1, inner_sub_process: 1, user_task: 3, inner_flow: 7},
				totalRunningInstancesVisibleByElement: {inner_sub_process: 1, user_task: 2, inner_flow: 7},
			}),
		).toEqual({
			parent_sub_process: counts({
				cancelledTokens: 1,
				visibleCancelledTokens: 1,
				cancelledChildTokens: 3,
				areAllTokensCanceled: true,
			}),
			inner_sub_process: counts({cancelledTokens: 1, visibleCancelledTokens: 1, areAllTokensCanceled: true}),
			user_task: counts({cancelledTokens: 3, visibleCancelledTokens: 2, areAllTokensCanceled: true}),
			inner_end: counts({areAllTokensCanceled: true}),
		});
		expect(
			getModificationsByElement(state, {businessObjects, totalRunningInstancesByElement: {parent_sub_process: 2}})
				.parent_sub_process,
		).toEqual(counts({cancelledTokens: 1, visibleCancelledTokens: 1}));
	});

	it('should predict whether every running element will be cancelled', () => {
		const statistics = [
			{elementId: 'task', active: 2, incidents: 0},
			{elementId: 'incident', active: 0, incidents: 1},
			{elementId: 'done', active: 0, incidents: 0},
		];
		const totalRunningInstancesByElement = {task: 2, incident: 1};
		const cancelAll = createModificationState([
			cancelToken({elementId: 'task', affectedTokenCount: 2}),
			cancelToken({elementId: 'incident', affectedTokenCount: 1}),
		]);
		const cancelTask = createModificationState([cancelToken({elementId: 'task', affectedTokenCount: 2})]);
		const cancelAndAdd = createModificationState([
			...cancelAll.modifications,
			addToken({elementId: 'done', scopeId: 'scope'}),
		]);
		const predict = (state: typeof cancelAll, stats: typeof statistics | undefined) =>
			willAllElementsBeCanceled(state, stats, getModificationsByElement(state, {totalRunningInstancesByElement}));

		expect(predict(cancelAll, statistics)).toBe(true);
		expect(predict(cancelTask, statistics)).toBe(false);
		expect(predict(cancelAndAdd, statistics)).toBe(false);
		expect(predict(cancelAll, undefined)).toBe(false);
		expect(predict(createModificationState(), [])).toBe(true);
	});
});
