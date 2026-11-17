/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {addToken, cancelToken, createModificationBusinessObjects, createModificationState, moveToken} from './mocks';
import type {ModificationState} from './modificationReducer';
import {getModificationsByElement} from './modificationSelectors';
import {
	getAncestorScopeType,
	getAvailableModifications,
	getModifiableElements,
	getRunningInstancesByElement,
	hasMultipleScopes,
	getTargetScopes,
	canBeAddedTokenAncestor,
	hasActivationInCanceledScope,
	withoutMovesFromCanceledScopes,
} from './modificationTargets';

const businessObjects = createModificationBusinessObjects();

function statistic(elementId: string, active: number, incidents = 0) {
	return {elementId, active, incidents};
}

function getOptions({
	state = createModificationState(),
	statistics,
	elementId,
	selectedElementInstanceKey = null,
	runningElementInstanceCount,
	isMultiInstanceBody = false,
	isSingleRunningInstanceResolved = false,
}: {
	state?: ModificationState;
	statistics: ReturnType<typeof statistic>[];
	elementId: string;
	selectedElementInstanceKey?: string | null;
	runningElementInstanceCount: number;
	isMultiInstanceBody?: boolean;
	isSingleRunningInstanceResolved?: boolean;
}) {
	const runningInstancesByElement = getRunningInstancesByElement(statistics, businessObjects);
	return getAvailableModifications({
		state,
		businessObjects,
		statistics,
		runningInstancesByElement,
		modificationsByElement: getModificationsByElement(state, {
			businessObjects,
			totalRunningInstancesByElement: runningInstancesByElement,
			totalRunningInstancesVisibleByElement: runningInstancesByElement,
		}),
		elementId,
		selectedElementInstanceKey,
		runningElementInstanceCount,
		isMultiInstanceBody,
		isSingleRunningInstanceResolved,
	});
}

describe('modificationTargets', () => {
	it('should count active and incident instances of known elements only', () => {
		expect(
			getRunningInstancesByElement(
				[statistic('service_task', 2, 1), statistic('unknown_task', 4), statistic('user_task', 0)],
				businessObjects,
			),
		).toEqual({service_task: 3, user_task: 0});
	});

	it('should detect multiple running scopes through nested sub processes', () => {
		const innerSubProcess = businessObjects.inner_sub_process;

		expect(hasMultipleScopes(innerSubProcess, {inner_sub_process: 1, parent_sub_process: 2})).toBe(true);
		expect(hasMultipleScopes(innerSubProcess, {inner_sub_process: 2})).toBe(true);
		expect(hasMultipleScopes(innerSubProcess, {inner_sub_process: 1, parent_sub_process: 1})).toBe(false);
		expect(hasMultipleScopes(businessObjects.service_task?.$parent, {process: 2})).toBe(true);
		expect(hasMultipleScopes(undefined, {})).toBe(false);
	});

	it('should offer single-instance options when one running instance is resolved', () => {
		expect(
			getOptions({
				statistics: [statistic('service_task', 1)],
				elementId: 'service_task',
				runningElementInstanceCount: 1,
				isSingleRunningInstanceResolved: true,
			}),
		).toEqual(['add', 'cancel-instance', 'move-instance']);
	});

	it('should offer all-instances options when several instances are running', () => {
		expect(
			getOptions({
				statistics: [statistic('service_task', 2, 1)],
				elementId: 'service_task',
				runningElementInstanceCount: 3,
			}),
		).toEqual(['add', 'cancel-all', 'move-all']);
	});

	it('should not offer adding a token when a specific instance is selected', () => {
		expect(
			getOptions({
				statistics: [statistic('service_task', 1)],
				elementId: 'service_task',
				selectedElementInstanceKey: 'instance-1',
				runningElementInstanceCount: 1,
				isSingleRunningInstanceResolved: true,
			}),
		).toEqual(['cancel-instance', 'move-instance']);
	});

	it('should not offer moving a sub process', () => {
		expect(
			getOptions({
				statistics: [statistic('parent_sub_process', 1)],
				elementId: 'parent_sub_process',
				runningElementInstanceCount: 1,
				isSingleRunningInstanceResolved: true,
			}),
		).toEqual(['add', 'cancel-all']);
	});

	it('should only offer adding to a multi-instance element through its body', () => {
		const statistics = [statistic('multi_instance_task', 3)];

		expect(getOptions({statistics, elementId: 'multi_instance_task', runningElementInstanceCount: 3})).toEqual([
			'cancel-all',
			'move-all',
		]);
		expect(
			getOptions({
				statistics,
				elementId: 'multi_instance_task',
				runningElementInstanceCount: 3,
				isMultiInstanceBody: true,
			}),
		).toEqual(['add', 'cancel-all', 'move-all']);
	});

	it('should not offer cancellation again after all tokens are canceled', () => {
		const state = createModificationState([cancelToken({elementId: 'service_task', affectedTokenCount: 2})]);

		expect(
			getOptions({
				state,
				statistics: [statistic('service_task', 2)],
				elementId: 'service_task',
				runningElementInstanceCount: 2,
			}),
		).toEqual(['add']);
	});

	it('should not offer cancellation again for an instance with a pending cancellation', () => {
		const state = createModificationState([cancelToken({elementId: 'service_task', elementInstanceKey: 'instance-1'})]);

		expect(
			getOptions({
				state,
				statistics: [statistic('service_task', 2)],
				elementId: 'service_task',
				selectedElementInstanceKey: 'instance-1',
				runningElementInstanceCount: 1,
				isSingleRunningInstanceResolved: true,
			}),
		).toEqual([]);
	});

	it('should offer adding to an idle element, also when its parent runs in several scopes', () => {
		expect(getOptions({statistics: [], elementId: 'user_task', runningElementInstanceCount: 0})).toEqual(['add']);
		expect(
			getOptions({
				statistics: [statistic('parent_sub_process', 2), statistic('inner_sub_process', 2)],
				elementId: 'user_task',
				runningElementInstanceCount: 0,
			}),
		).toEqual(['add']);
	});

	it('should exclude the move source from the move targets', () => {
		const statistics = [statistic('service_task', 1)];
		const runningInstancesByElement = getRunningInstancesByElement(statistics, businessObjects);
		const enabled = getModifiableElements(
			createModificationState(),
			businessObjects,
			statistics,
			runningInstancesByElement,
		);
		const moving = getModifiableElements(
			createModificationState([], {status: 'moving-token', sourceElementIdForMoveOperation: 'service_task'}),
			businessObjects,
			statistics,
			runningInstancesByElement,
		);

		expect(enabled).toContain('service_task');
		expect(moving).not.toContain('service_task');
		expect(moving).toContain('user_task');
	});

	it('should only accept running enclosing instances as the parent scope of an added token', () => {
		const instance = (elementId: string, overrides: {state?: string; type?: string} = {}) => ({
			elementId,
			state: 'ACTIVE',
			type: 'SUB_PROCESS',
			...overrides,
		});

		expect(canBeAddedTokenAncestor(businessObjects, 'user_task', instance('inner_sub_process'))).toBe(true);
		expect(canBeAddedTokenAncestor(businessObjects, 'user_task', instance('parent_sub_process'))).toBe(true);
		expect(canBeAddedTokenAncestor(businessObjects, 'user_task', instance('service_task'))).toBe(false);
		expect(
			canBeAddedTokenAncestor(businessObjects, 'user_task', instance('inner_sub_process', {state: 'COMPLETED'})),
		).toBe(false);
		expect(
			canBeAddedTokenAncestor(
				businessObjects,
				'user_task',
				instance('inner_sub_process', {type: 'MULTI_INSTANCE_BODY'}),
			),
		).toBe(false);
	});

	it('should not offer all-instance actions while a single token of the element is cancelled or moved', () => {
		const state = createModificationState([
			moveToken({elementId: 'service_task', targetElementId: 'user_task', elementInstanceKey: 'instance-1'}),
		]);

		expect(
			getOptions({
				state,
				statistics: [statistic('service_task', 2)],
				elementId: 'service_task',
				runningElementInstanceCount: 2,
			}),
		).toEqual(['add']);
	});

	it('should check all-instance actions of a multi-instance body against every inner token', () => {
		const state = createModificationState([
			moveToken({elementId: 'multi_instance_task', targetElementId: 'service_task', elementInstanceKey: 'inner-1'}),
		]);

		expect(
			getOptions({
				state,
				statistics: [statistic('multi_instance_task', 3)],
				elementId: 'multi_instance_task',
				selectedElementInstanceKey: 'body-1',
				runningElementInstanceCount: 3,
				isMultiInstanceBody: true,
				isSingleRunningInstanceResolved: true,
			}),
		).toEqual([]);
	});

	it('should not accept a parent scope beyond the first multi-instance boundary', () => {
		const process: BusinessObject = {id: 'process', name: 'Process', $type: 'bpmn:Process'};
		const outer: BusinessObject = {id: 'outer', name: 'Outer', $type: 'bpmn:SubProcess', $parent: process};
		const multiInstance: BusinessObject = {
			id: 'multi',
			name: 'Multi',
			$type: 'bpmn:SubProcess',
			$parent: outer,
			loopCharacteristics: {$type: 'bpmn:MultiInstanceLoopCharacteristics', isSequential: false},
		};
		const task: BusinessObject = {id: 'task', name: 'Task', $type: 'bpmn:UserTask', $parent: multiInstance};
		const objects = {outer, multi: multiInstance, task};
		const instance = (elementId: string) => ({elementId, state: 'ACTIVE', type: 'SUB_PROCESS'});

		expect(canBeAddedTokenAncestor(objects, 'task', instance('multi'))).toBe(true);
		expect(canBeAddedTokenAncestor(objects, 'task', instance('outer'))).toBe(false);
	});

	it('should detect tokens added or moved into a scope that is planned to be cancelled', () => {
		const runningInstancesByElement = {parent_sub_process: 1};
		const conflicts = (modifications: Parameters<typeof createModificationState>[0]) => {
			const state = createModificationState(modifications);
			return hasActivationInCanceledScope(
				state,
				businessObjects,
				getModificationsByElement(state, {
					businessObjects,
					totalRunningInstancesByElement: runningInstancesByElement,
					totalRunningInstancesVisibleByElement: runningInstancesByElement,
				}),
			);
		};
		const cancelParent = cancelToken({elementId: 'parent_sub_process', affectedTokenCount: 1});

		expect(conflicts([cancelParent, addToken({elementId: 'user_task', scopeId: 'a'})])).toBe(true);
		expect(conflicts([addToken({elementId: 'user_task', scopeId: 'a'}), cancelParent])).toBe(true);
		expect(conflicts([cancelParent, moveToken({elementId: 'service_task', targetElementId: 'user_task'})])).toBe(true);
		expect(
			conflicts([
				cancelToken({elementId: 'inner_sub_process', elementInstanceKey: 'inner-1'}),
				addToken({
					elementId: 'user_task',
					scopeId: 'a',
					ancestorElement: {elementId: 'inner_sub_process', instanceKey: 'inner-1'},
				}),
			]),
		).toBe(true);
		expect(conflicts([cancelParent, addToken({elementId: 'service_task', scopeId: 'a'})])).toBe(false);
	});

	it('should drop moves of all tokens whose source scope is planned to be cancelled', () => {
		const runningInstancesByElement = {parent_sub_process: 1, inner_sub_process: 1, user_task: 1};
		const moveAll = moveToken({elementId: 'user_task', targetElementId: 'service_task'});
		const moveOne = moveToken({elementId: 'user_task', targetElementId: 'service_task', elementInstanceKey: 'task-1'});
		const effective = (modifications: Parameters<typeof createModificationState>[0]) => {
			const state = createModificationState(modifications);
			return withoutMovesFromCanceledScopes(
				state,
				businessObjects,
				getModificationsByElement(state, {
					businessObjects,
					totalRunningInstancesByElement: runningInstancesByElement,
					totalRunningInstancesVisibleByElement: runningInstancesByElement,
				}),
			).modifications;
		};
		const cancelParent = cancelToken({elementId: 'parent_sub_process', affectedTokenCount: 1});

		expect(effective([moveAll, cancelParent])).toEqual([cancelParent]);
		expect(effective([moveOne, cancelParent])).toEqual([moveOne, cancelParent]);
		expect(effective([moveAll])).toEqual([moveAll]);
	});

	it('should only check the reused ancestor of an ancestor-scoped token against cancellations', () => {
		const runningInstancesByElement = {parent_sub_process: 2, inner_sub_process: 1};
		const conflicts = (modifications: Parameters<typeof createModificationState>[0]) => {
			const state = createModificationState(modifications);
			return hasActivationInCanceledScope(
				state,
				businessObjects,
				getModificationsByElement(state, {
					businessObjects,
					totalRunningInstancesByElement: runningInstancesByElement,
					totalRunningInstancesVisibleByElement: runningInstancesByElement,
				}),
			);
		};
		const addUnderOuter2 = addToken({
			elementId: 'user_task',
			scopeId: 'a',
			ancestorElement: {elementId: 'parent_sub_process', instanceKey: 'outer-2'},
			parentScopeIds: {inner_sub_process: 'new-inner'},
		});

		expect(conflicts([cancelToken({elementId: 'inner_sub_process', affectedTokenCount: 1}), addUnderOuter2])).toBe(
			false,
		);
		expect(conflicts([cancelToken({elementId: 'parent_sub_process', affectedTokenCount: 2}), addUnderOuter2])).toBe(
			true,
		);
	});

	it('should not offer adding a token inside a scope that is planned to be cancelled', () => {
		const state = createModificationState([cancelToken({elementId: 'parent_sub_process', affectedTokenCount: 1})]);

		expect(
			getOptions({
				state,
				statistics: [statistic('parent_sub_process', 1)],
				elementId: 'user_task',
				runningElementInstanceCount: 0,
			}),
		).toEqual([]);
	});

	it('should not ask for an ancestor scope when only the target element runs several times', () => {
		expect(
			getAncestorScopeType(businessObjects, 'service_task', 'user_task', {
				user_task: 2,
				inner_sub_process: 1,
				parent_sub_process: 1,
			}),
		).toBe(undefined);
	});

	it('should not offer adding a token whose parent scope lies beyond an inactive multi-instance boundary', () => {
		const process: BusinessObject = {id: 'process', name: 'Process', $type: 'bpmn:Process'};
		const outer: BusinessObject = {id: 'outer', name: 'Outer', $type: 'bpmn:SubProcess', $parent: process};
		const multiInstance: BusinessObject = {
			id: 'multi',
			name: 'Multi',
			$type: 'bpmn:SubProcess',
			$parent: outer,
			loopCharacteristics: {$type: 'bpmn:MultiInstanceLoopCharacteristics', isSequential: false},
		};
		const task: BusinessObject = {id: 'task', name: 'Task', $type: 'bpmn:UserTask', $parent: multiInstance};
		const objects = {outer, multi: multiInstance, task};
		const modifiable = (runningInstancesByElement: {[elementId: string]: number}) =>
			getModifiableElements(createModificationState(), objects, [], runningInstancesByElement);

		expect(modifiable({outer: 2})).not.toContain('task');
		expect(modifiable({outer: 1})).not.toContain('task');
		expect(modifiable({outer: 1, multi: 1})).toContain('task');
		expect(modifiable({outer: 2, multi: 2})).toContain('task');
		expect(getTargetScopes(task, {outer: 2})).toMatchObject({hasMultipleScopesWithinBoundary: false});
		expect(getTargetScopes(task, {outer: 2, multi: 2})).toMatchObject({hasMultipleScopesWithinBoundary: true});
		expect(getTargetScopes(task, {outer: 2, multi: 1})).toEqual({
			isEffectivelySingleScope: true,
			hasMultipleScopesWithinBoundary: false,
			requiresParentScopeSelection: true,
		});
		expect(modifiable({outer: 2, multi: 1})).toContain('task');
	});

	it('should not allow moving all tokens into a scope below an ambiguous multi-instance parent', () => {
		const process: BusinessObject = {id: 'process', name: 'Process', $type: 'bpmn:Process'};
		const rootTask: BusinessObject = {id: 'root_task', name: 'Root Task', $type: 'bpmn:UserTask', $parent: process};
		const multiInstance: BusinessObject = {
			id: 'multi',
			name: 'Multi',
			$type: 'bpmn:SubProcess',
			$parent: process,
			loopCharacteristics: {$type: 'bpmn:MultiInstanceLoopCharacteristics', isSequential: false},
		};
		const nested: BusinessObject = {id: 'nested', name: 'Nested', $type: 'bpmn:SubProcess', $parent: multiInstance};
		const task: BusinessObject = {id: 'task', name: 'Task', $type: 'bpmn:UserTask', $parent: nested};
		const objects = {root_task: rootTask, multi: multiInstance, nested, task};
		const moveAll = createModificationState([], {status: 'moving-token', sourceElementIdForMoveOperation: 'root_task'});

		expect(getModifiableElements(moveAll, objects, [], {multi: 2, nested: 1})).not.toContain('task');
		expect(getModifiableElements(moveAll, objects, [], {multi: 1, nested: 1})).toContain('task');
	});

	describe('moving all tokens into a multi-instance sub process inside an outer sub process', () => {
		const process: BusinessObject = {id: 'process', name: 'Process', $type: 'bpmn:Process'};
		const rootTask: BusinessObject = {id: 'root_task', name: 'Root Task', $type: 'bpmn:UserTask', $parent: process};
		const outer: BusinessObject = {id: 'outer', name: 'Outer', $type: 'bpmn:SubProcess', $parent: process};
		const outerTask: BusinessObject = {id: 'outer_task', name: 'Outer Task', $type: 'bpmn:UserTask', $parent: outer};
		const multiInstance: BusinessObject = {
			id: 'multi',
			name: 'Multi',
			$type: 'bpmn:SubProcess',
			$parent: outer,
			loopCharacteristics: {$type: 'bpmn:MultiInstanceLoopCharacteristics', isSequential: false},
		};
		const source: BusinessObject = {id: 'source', name: 'Source', $type: 'bpmn:UserTask', $parent: multiInstance};
		const task: BusinessObject = {id: 'task', name: 'Task', $type: 'bpmn:UserTask', $parent: multiInstance};
		const objects = {root_task: rootTask, outer, outer_task: outerTask, multi: multiInstance, source, task};

		it.for([
			{outer: 0, multi: 0, source: 'root_task', isTarget: false, scopeType: undefined},
			{outer: 0, multi: 0, source: 'outer_task', isTarget: false, scopeType: undefined},
			{outer: 0, multi: 0, source: 'source', isTarget: false, scopeType: undefined},
			{outer: 0, multi: 1, source: 'root_task', isTarget: true, scopeType: undefined},
			{outer: 0, multi: 1, source: 'outer_task', isTarget: true, scopeType: undefined},
			{outer: 0, multi: 1, source: 'source', isTarget: true, scopeType: undefined},
			{outer: 0, multi: 2, source: 'root_task', isTarget: false, scopeType: 'inferred'},
			{outer: 0, multi: 2, source: 'outer_task', isTarget: false, scopeType: 'inferred'},
			{outer: 0, multi: 2, source: 'source', isTarget: false, scopeType: 'sourceParent'},
			{outer: 1, multi: 0, source: 'root_task', isTarget: false, scopeType: undefined},
			{outer: 1, multi: 0, source: 'outer_task', isTarget: false, scopeType: undefined},
			{outer: 1, multi: 0, source: 'source', isTarget: false, scopeType: undefined},
			{outer: 1, multi: 1, source: 'root_task', isTarget: true, scopeType: undefined},
			{outer: 1, multi: 1, source: 'outer_task', isTarget: true, scopeType: undefined},
			{outer: 1, multi: 1, source: 'source', isTarget: true, scopeType: undefined},
			{outer: 1, multi: 2, source: 'root_task', isTarget: false, scopeType: 'inferred'},
			{outer: 1, multi: 2, source: 'outer_task', isTarget: false, scopeType: 'inferred'},
			{outer: 1, multi: 2, source: 'source', isTarget: false, scopeType: 'sourceParent'},
			{outer: 2, multi: 0, source: 'root_task', isTarget: false, scopeType: 'inferred'},
			{outer: 2, multi: 0, source: 'outer_task', isTarget: false, scopeType: 'inferred'},
			{outer: 2, multi: 0, source: 'source', isTarget: false, scopeType: 'inferred'},
			{outer: 2, multi: 1, source: 'root_task', isTarget: false, scopeType: 'inferred'},
			{outer: 2, multi: 1, source: 'outer_task', isTarget: false, scopeType: 'inferred'},
			{outer: 2, multi: 1, source: 'source', isTarget: true, scopeType: 'sourceParent'},
			{outer: 2, multi: 2, source: 'root_task', isTarget: false, scopeType: 'inferred'},
			{outer: 2, multi: 2, source: 'outer_task', isTarget: false, scopeType: 'inferred'},
			{outer: 2, multi: 2, source: 'source', isTarget: false, scopeType: 'sourceParent'},
		] as const)(
			'should treat outer=$outer, multi=$multi, source=$source as target $isTarget with scope type $scopeType',
			({outer: outerCount, multi, source: sourceElementId, isTarget, scopeType}) => {
				const runningInstancesByElement = {outer: outerCount, multi};
				const moveAll = createModificationState([], {
					status: 'moving-token',
					sourceElementIdForMoveOperation: sourceElementId,
				});

				expect(getModifiableElements(moveAll, objects, [], runningInstancesByElement).includes('task')).toBe(isTarget);
				expect(getAncestorScopeType(objects, sourceElementId, 'task', runningInstancesByElement)).toBe(scopeType);
			},
		);
	});

	it('should infer the ancestor scope for a multi-instance source', () => {
		const runningInstancesByElement = {process: 2};

		expect(
			getAncestorScopeType(businessObjects, 'service_task', 'multi_instance_task', runningInstancesByElement),
		).toBe('sourceParent');
		expect(
			getAncestorScopeType(businessObjects, 'multi_instance_task', 'service_task', runningInstancesByElement),
		).toBe('inferred');
	});

	it('should resolve the ancestor scope type for targets with multiple scopes', () => {
		const runningInstancesByElement = {parent_sub_process: 1, inner_sub_process: 2};

		expect(getAncestorScopeType(businessObjects, 'service_task', 'user_task', runningInstancesByElement)).toBe(
			'inferred',
		);
		expect(getAncestorScopeType(businessObjects, 'inner_end', 'user_task', runningInstancesByElement)).toBe(
			'sourceParent',
		);
		expect(
			getAncestorScopeType(businessObjects, 'service_task', 'multi_instance_task', runningInstancesByElement),
		).toBe(undefined);
	});
});
