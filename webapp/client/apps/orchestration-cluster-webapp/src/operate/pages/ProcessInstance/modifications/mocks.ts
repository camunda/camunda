/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject, BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {
	initialModificationState,
	type AddTokenModification,
	type CancelTokenModification,
	type Modification,
	type ModificationState,
	type MoveTokenModification,
	type VariableModificationPayload,
} from './modificationReducer';

function appendChild(parent: BusinessObject, child: Omit<BusinessObject, '$parent'>) {
	const businessObject: BusinessObject = {...child, $parent: parent};
	parent.flowElements = [...(parent.flowElements ?? []), businessObject];
	return businessObject;
}

function createModificationBusinessObjects(): BusinessObjects {
	const process: BusinessObject = {id: 'process', name: 'Process', $type: 'bpmn:Process'};
	const parent = appendChild(process, {id: 'parent_sub_process', name: 'Parent Sub Process', $type: 'bpmn:SubProcess'});
	const inner = appendChild(parent, {id: 'inner_sub_process', name: 'Inner Sub Process', $type: 'bpmn:SubProcess'});
	const businessObjects = [
		parent,
		inner,
		appendChild(inner, {id: 'user_task', name: 'User Task', $type: 'bpmn:UserTask'}),
		appendChild(inner, {id: 'inner_flow', name: '', $type: 'bpmn:SequenceFlow'}),
		appendChild(inner, {id: 'inner_end', name: '', $type: 'bpmn:EndEvent'}),
		appendChild(process, {id: 'service_task', name: 'Service Task', $type: 'bpmn:ServiceTask'}),
		appendChild(process, {
			id: 'multi_instance_task',
			name: 'Multi Instance Task',
			$type: 'bpmn:ServiceTask',
			loopCharacteristics: {$type: 'bpmn:MultiInstanceLoopCharacteristics', isSequential: false},
		}),
	];

	return Object.fromEntries(businessObjects.map((businessObject) => [businessObject.id, businessObject]));
}

function createIdSequence(prefix = 'scope') {
	let index = 0;
	return () => `${prefix}-${index++}`;
}

function createModificationState(
	modifications: Modification[] = [],
	overrides: Partial<ModificationState> = {},
): ModificationState {
	return {...initialModificationState, status: 'enabled', modifications, ...overrides};
}

function tokenBase(elementId: string) {
	return {element: {id: elementId, name: elementId}, affectedTokenCount: 1, visibleAffectedTokenCount: 1};
}

function addToken({elementId, ...payload}: Partial<AddTokenModification> & {elementId: string; scopeId: string}) {
	return {
		type: 'token' as const,
		payload: {operation: 'ADD_TOKEN' as const, ...tokenBase(elementId), parentScopeIds: {}, ...payload},
	};
}

function cancelToken({elementId, ...payload}: Partial<CancelTokenModification> & {elementId: string}) {
	return {type: 'token' as const, payload: {operation: 'CANCEL_TOKEN' as const, ...tokenBase(elementId), ...payload}};
}

function moveToken({
	elementId,
	targetElementId,
	...payload
}: Partial<MoveTokenModification> & {elementId: string; targetElementId: string}) {
	const targetElement = {id: targetElementId, name: targetElementId};
	return {
		type: 'token' as const,
		payload: {
			operation: 'MOVE_TOKEN' as const,
			...tokenBase(elementId),
			targetElement,
			scopeIds: [],
			parentScopeIds: {},
			...payload,
		},
	};
}

function variable(
	payload: Pick<VariableModificationPayload, 'operation' | 'scopeId' | 'name' | 'newValue'> &
		Partial<VariableModificationPayload>,
) {
	return {type: 'variable' as const, payload: {id: payload.name, elementName: 'element-name', ...payload}};
}

export {
	addToken,
	cancelToken,
	createIdSequence,
	createModificationBusinessObjects,
	createModificationState,
	moveToken,
	variable,
};
