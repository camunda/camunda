/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {addToken, cancelToken, createModificationBusinessObjects, createModificationState, moveToken} from './mocks';
import {
	getChildPlaceholders,
	getLoadedInstanceAncestors,
	getModificationPlaceholders,
	hasChildPlaceholders,
} from './historyPlaceholders';

const businessObjects = createModificationBusinessObjects();
const PROCESS_INSTANCE_KEY = '2251799813685249';

describe('historyPlaceholders', () => {
	it('should create placeholders for new parent scopes down to the added element', () => {
		const state = createModificationState([
			addToken({
				elementId: 'user_task',
				scopeId: 'task-scope',
				parentScopeIds: {parent_sub_process: 'parent-scope', inner_sub_process: 'inner-scope'},
			}),
		]);
		const placeholders = getModificationPlaceholders(state, businessObjects, 'process', PROCESS_INSTANCE_KEY);

		expect(getChildPlaceholders(placeholders, PROCESS_INSTANCE_KEY, 'process', true)).toEqual([
			expect.objectContaining({elementId: 'parent_sub_process', elementInstanceKey: 'parent-scope'}),
		]);
		expect(getChildPlaceholders(placeholders, 'parent-scope', 'parent_sub_process', true)).toEqual([
			expect.objectContaining({elementId: 'inner_sub_process', elementInstanceKey: 'inner-scope'}),
		]);
		expect(getChildPlaceholders(placeholders, 'inner-scope', 'inner_sub_process', true)).toEqual([
			expect.objectContaining({elementId: 'user_task', elementInstanceKey: 'task-scope'}),
		]);
		expect(hasChildPlaceholders(placeholders, 'task-scope')).toBe(false);
	});

	it('should place an ancestor-scoped token below the selected ancestor instance', () => {
		const state = createModificationState([
			addToken({
				elementId: 'user_task',
				scopeId: 'task-scope',
				ancestorElement: {elementId: 'inner_sub_process', instanceKey: 'running-inner'},
			}),
		]);
		const placeholders = getModificationPlaceholders(state, businessObjects, 'process', PROCESS_INSTANCE_KEY);

		expect(getChildPlaceholders(placeholders, 'running-inner', 'inner_sub_process', true)).toEqual([
			expect.objectContaining({elementId: 'user_task', elementInstanceKey: 'task-scope'}),
		]);
	});

	it('should keep top-level tokens next to newly created parent scopes', () => {
		const state = createModificationState([
			addToken({elementId: 'service_task', scopeId: 'service-scope'}),
			addToken({
				elementId: 'user_task',
				scopeId: 'task-scope',
				parentScopeIds: {parent_sub_process: 'parent-scope', inner_sub_process: 'inner-scope'},
			}),
		]);
		const placeholders = getModificationPlaceholders(state, businessObjects, 'process', PROCESS_INSTANCE_KEY);

		expect(
			getChildPlaceholders(placeholders, PROCESS_INSTANCE_KEY, 'process', true).map(
				({elementInstanceKey}) => elementInstanceKey,
			),
		).toEqual(['service-scope', 'parent-scope']);
	});

	it('should attach inferred placeholders only to running scopes of the parent element', () => {
		const state = createModificationState([addToken({elementId: 'user_task', scopeId: 'task-scope'})]);
		const placeholders = getModificationPlaceholders(state, businessObjects, 'process', PROCESS_INSTANCE_KEY);

		expect(getChildPlaceholders(placeholders, 'running-inner', 'inner_sub_process', true)).toHaveLength(1);
		expect(getChildPlaceholders(placeholders, 'completed-inner', 'inner_sub_process', false)).toEqual([]);
	});

	it('should show a moved token under each running parent scope unless its destination is known', () => {
		const state = createModificationState([
			addToken({
				elementId: 'user_task',
				scopeId: 'added',
				ancestorElement: {elementId: 'inner_sub_process', instanceKey: 'inner-1'},
			}),
			moveToken({elementId: 'inner_end', targetElementId: 'user_task', scopeIds: ['moved-all']}),
			moveToken({
				elementId: 'inner_end',
				elementInstanceKey: 'end-1',
				targetElementId: 'user_task',
				scopeIds: ['moved-one'],
				ancestorScopeType: 'sourceParent',
				ancestorElement: {elementId: 'inner_sub_process', instanceKey: 'inner-2'},
			}),
		]);
		const placeholders = getModificationPlaceholders(state, businessObjects, 'process', PROCESS_INSTANCE_KEY);
		const childKeys = (scopeKey: string) =>
			getChildPlaceholders(placeholders, scopeKey, 'inner_sub_process', true).map(
				({elementInstanceKey}) => elementInstanceKey,
			);

		expect(childKeys('inner-1')).toEqual(['added', 'moved-all']);
		expect(childKeys('inner-2')).toEqual(['moved-all', 'moved-one']);
	});

	it('should resolve the ancestors of an instance from the loaded history scopes', () => {
		const scopes = [
			{key: PROCESS_INSTANCE_KEY, items: [{elementId: 'parent_sub_process', elementInstanceKey: 'outer-1'}]},
			{key: 'outer-1', items: [{elementId: 'inner_sub_process', elementInstanceKey: 'inner-1'}]},
			{key: 'inner-1', items: [{elementId: 'user_task', elementInstanceKey: 'task-1'}]},
		];

		expect(getLoadedInstanceAncestors(scopes, 'task-1', PROCESS_INSTANCE_KEY, 'process')).toEqual([
			{elementId: 'inner_sub_process', instanceKey: 'inner-1'},
			{elementId: 'parent_sub_process', instanceKey: 'outer-1'},
			{elementId: 'process', instanceKey: PROCESS_INSTANCE_KEY},
		]);
		expect(getLoadedInstanceAncestors(scopes, 'unknown', PROCESS_INSTANCE_KEY, 'process')).toEqual([]);
	});

	it('should list moved tokens and ignore cancellations', () => {
		const state = createModificationState([
			cancelToken({elementId: 'service_task'}),
			moveToken({elementId: 'multi_instance_task', targetElementId: 'service_task', scopeIds: ['move-1', 'move-2']}),
		]);
		const placeholders = getModificationPlaceholders(state, businessObjects, 'process', PROCESS_INSTANCE_KEY);

		expect(
			getChildPlaceholders(placeholders, PROCESS_INSTANCE_KEY, 'process', true).map(
				({elementInstanceKey}) => elementInstanceKey,
			),
		).toEqual(['move-1', 'move-2']);
	});
});
