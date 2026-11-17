/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	addToken,
	cancelToken,
	createIdSequence,
	createModificationBusinessObjects,
	createModificationState,
	moveToken,
} from './mocks';
import {modificationReducer, type ModificationState} from './modificationReducer';
import {
	createAddTokenModification,
	createCancelTokenModification,
	createFinishAddingTokenAction,
	createFinishMovingTokenAction,
} from './modificationOperations';

const businessObjects = createModificationBusinessObjects();

function movingFrom(source: string, key: string | null = null): ModificationState {
	return createModificationState([], {
		status: 'moving-token',
		sourceElementIdForMoveOperation: source,
		sourceElementInstanceKeyForMoveOperation: key,
	});
}

describe('modificationOperations', () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('should share staged parent scopes and only create scopes for parents without running instances', () => {
		const createId = createIdSequence();
		const input = {businessObjects, elementId: 'user_task', bpmnProcessId: 'process', createId};
		const first = createAddTokenModification(createModificationState(), input);
		const cancelParent = cancelToken({elementId: 'inner_sub_process'});
		const moveIntoParent = moveToken({elementId: 'a', targetElementId: 'b', parentScopeIds: {inner_sub_process: 'm'}});

		expect(first).toEqual({
			operation: 'ADD_TOKEN',
			scopeId: 'scope-0',
			element: {id: 'user_task', name: 'User Task'},
			affectedTokenCount: 1,
			visibleAffectedTokenCount: 1,
			parentScopeIds: {inner_sub_process: 'scope-1', parent_sub_process: 'scope-2'},
		});
		expect(
			createAddTokenModification(createModificationState([cancelParent, {type: 'token', payload: first}]), input),
		).toMatchObject({
			scopeId: 'scope-3',
			parentScopeIds: {inner_sub_process: 'scope-1', parent_sub_process: 'scope-2'},
		});
		expect(createAddTokenModification(createModificationState([cancelParent]), input).parentScopeIds).toEqual({
			inner_sub_process: 'scope-5',
			parent_sub_process: 'scope-6',
		});
		expect(createAddTokenModification(createModificationState([moveIntoParent]), input).parentScopeIds).toEqual({
			inner_sub_process: 'm',
			parent_sub_process: 'scope-8',
		});
		expect(
			createAddTokenModification(createModificationState(), {
				...input,
				runningInstancesByElement: {parent_sub_process: 1, inner_sub_process: 0},
			}).parentScopeIds,
		).toEqual({inner_sub_process: 'scope-10'});
		expect(
			createAddTokenModification(createModificationState(), {businessObjects, elementId: 'unknown'}),
		).toMatchObject({element: {id: 'unknown', name: 'unknown'}, parentScopeIds: {}});
	});

	it('should not share a staged parent scope when an ancestor runs in several instances', () => {
		const staged = moveToken({
			elementId: 'a',
			targetElementId: 'user_task',
			parentScopeIds: {inner_sub_process: 'staged'},
		});
		const input = {
			businessObjects,
			elementId: 'user_task',
			bpmnProcessId: 'process',
			createId: createIdSequence(),
			runningInstancesByElement: {parent_sub_process: 2},
		};

		expect(createAddTokenModification(createModificationState([staged]), input).parentScopeIds).toEqual({
			inner_sub_process: 'scope-1',
		});
	});

	it('should place a single moved token under the ancestor it shares with its source instance', () => {
		const state = createModificationState([], {
			status: 'moving-token',
			sourceElementIdForMoveOperation: 'service_task',
			sourceElementInstanceKeyForMoveOperation: 'source-1',
		});
		const input = {
			businessObjects,
			affectedTokenCount: 1,
			visibleAffectedTokenCount: 1,
			bpmnProcessId: 'process',
			ancestorScopeType: 'inferred' as const,
			sourceAncestors: [
				{elementId: 'parent_sub_process', instanceKey: 'outer-1'},
				{elementId: 'process', instanceKey: 'instance'},
			],
			runningInstancesByElement: {parent_sub_process: 2, inner_sub_process: 1},
			createId: createIdSequence(),
		};

		expect(createFinishMovingTokenAction(state, {...input, targetElementId: 'user_task'}).modification).toMatchObject({
			ancestorElement: {elementId: 'parent_sub_process', instanceKey: 'outer-1'},
			parentScopeIds: {inner_sub_process: 'scope-1'},
		});
		expect(
			createFinishMovingTokenAction(state, {...input, targetElementId: 'inner_sub_process'}).modification,
		).toMatchObject({ancestorElement: {elementId: 'parent_sub_process', instanceKey: 'outer-1'}, parentScopeIds: {}});
		expect(
			createFinishMovingTokenAction(state, {...input, sourceAncestors: [], targetElementId: 'user_task'}).modification,
		).not.toHaveProperty('ancestorElement');
	});

	it('should generate unique scope ids with and without crypto.randomUUID', () => {
		const {crypto} = globalThis;
		const add = () => createAddTokenModification(createModificationState(), {businessObjects, elementId: 'user_task'});
		const [first, second] = [add(), add()];

		expect(first.scopeId).toMatch(/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/);
		expect(first.scopeId).not.toBe(second.scopeId);

		vi.stubGlobal('crypto', {getRandomValues: crypto.getRandomValues.bind(crypto)});

		expect(add().scopeId).toMatch(/^[\da-f]{32}$/);
	});

	it.for([
		{elementInstanceKey: undefined, affectedTokenCount: 3, visibleAffectedTokenCount: 2},
		{elementInstanceKey: '2251799813685250', affectedTokenCount: 1, visibleAffectedTokenCount: 1},
	])('should stage a cancellation for instance $elementInstanceKey', (input) => {
		expect(createCancelTokenModification({businessObjects, elementId: 'service_task', ...input})).toEqual({
			operation: 'CANCEL_TOKEN',
			element: {id: 'service_task', name: 'Service Task'},
			...input,
		});
	});

	it('should finish adding a token below the ancestor with new in-between scopes', () => {
		const staged = addToken({elementId: 'inner_end', scopeId: 's', parentScopeIds: {inner_sub_process: 'staged'}});
		const adding = modificationReducer(createModificationState([staged]), {
			type: 'startAddingToken',
			elementId: 'user_task',
		});
		const action = createFinishAddingTokenAction(adding, {
			businessObjects,
			ancestorElementId: 'parent_sub_process',
			ancestorElementInstanceKey: '2251799813685251',
			createId: createIdSequence(),
		});

		expect(action.modification).toEqual({
			operation: 'ADD_TOKEN',
			scopeId: 'scope-0',
			element: {id: 'user_task', name: 'User Task'},
			affectedTokenCount: 1,
			visibleAffectedTokenCount: 1,
			ancestorElement: {instanceKey: '2251799813685251', elementId: 'parent_sub_process'},
			parentScopeIds: {inner_sub_process: 'scope-1'},
		});
		expect(modificationReducer(adding, action)).toMatchObject({
			status: 'enabled',
			sourceElementIdForAddOperation: null,
			modifications: [staged, {type: 'token', payload: action.modification}],
		});
	});

	it.for([
		{ancestorElementId: undefined, ancestorElementInstanceKey: '1', source: 'user_task'},
		{ancestorElementId: 'parent_sub_process', ancestorElementInstanceKey: undefined, source: 'user_task'},
		{ancestorElementId: 'parent_sub_process', ancestorElementInstanceKey: '1', source: null},
	])('should finish adding without staging for $ancestorElementId/$ancestorElementInstanceKey/$source', (input) => {
		const {source, ...ancestor} = input;
		const state = createModificationState([], {status: 'adding-token', sourceElementIdForAddOperation: source});

		expect(createFinishAddingTokenAction(state, {businessObjects, ...ancestor})).toEqual({type: 'finishAddingToken'});
	});

	it.for([
		{source: 'service_task', key: null, affected: 3, scopeIds: ['scope-0', 'scope-1', 'scope-2']},
		{source: 'multi_instance_task', key: null, affected: 3, scopeIds: ['scope-0']},
		{source: 'service_task', key: '2251799813685252', affected: 1, scopeIds: ['scope-0']},
	])('should finish moving $affected tokens from $source with key $key', ({source, key, affected, scopeIds}) => {
		const {modification} = createFinishMovingTokenAction(movingFrom(source, key), {
			businessObjects,
			affectedTokenCount: affected,
			visibleAffectedTokenCount: affected,
			bpmnProcessId: 'process',
			targetElementId: 'user_task',
			ancestorScopeType: 'inferred',
			createId: createIdSequence(),
		});

		expect(modification).toEqual({
			operation: 'MOVE_TOKEN',
			element: {id: source, name: businessObjects[source]?.name},
			elementInstanceKey: key ?? undefined,
			targetElement: {id: 'user_task', name: 'User Task'},
			affectedTokenCount: affected,
			visibleAffectedTokenCount: affected,
			scopeIds,
			parentScopeIds: {
				inner_sub_process: `scope-${scopeIds.length}`,
				parent_sub_process: `scope-${scopeIds.length + 1}`,
			},
			ancestorScopeType: 'inferred',
		});
	});

	it('should finish moving without staging or parent scopes when inputs are missing', () => {
		const input = {businessObjects, affectedTokenCount: 1, visibleAffectedTokenCount: 1, createId: createIdSequence()};

		expect(createFinishMovingTokenAction(movingFrom('service_task'), input)).toEqual({type: 'finishMovingToken'});
		expect(createFinishMovingTokenAction(createModificationState(), {...input, targetElementId: 'user_task'})).toEqual({
			type: 'finishMovingToken',
		});
		expect(
			createFinishMovingTokenAction(movingFrom('service_task'), {...input, targetElementId: 'user_task'}).modification,
		).toMatchObject({scopeIds: ['scope-0'], parentScopeIds: {}});
	});
});
