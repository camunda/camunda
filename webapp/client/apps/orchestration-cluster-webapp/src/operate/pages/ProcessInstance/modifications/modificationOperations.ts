/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from '#/operate/shared/utils/elements';
import {getElementName} from '#/operate/pages/Processes/getElementName';
import type {
	AddTokenModification,
	AncestorScopeType,
	CancelTokenModification,
	ModificationAction,
	ModificationState,
	ScopeIdsByElement,
} from './modificationReducer';
import {getElementModifications} from './modificationSelectors';

type GenerateId = () => string;

type TokenContext = {businessObjects: BusinessObjects; createId?: GenerateId};

type AddTokenInput = TokenContext & {elementId: string; bpmnProcessId?: string};

type CancelTokenInput = Omit<CancelTokenModification, 'operation' | 'element'> & {
	businessObjects: BusinessObjects;
	elementId: string;
};

type FinishAddingTokenInput = TokenContext & {ancestorElementId?: string; ancestorElementInstanceKey?: string};

type FinishMovingTokenInput = TokenContext & {
	affectedTokenCount: number;
	visibleAffectedTokenCount: number;
	bpmnProcessId?: string;
	targetElementId?: string;
	ancestorScopeType?: AncestorScopeType;
};

function generateId() {
	if (typeof crypto.randomUUID === 'function') {
		return crypto.randomUUID();
	}

	const values = crypto.getRandomValues(new Uint32Array(4));
	return Array.from(values, (value) => value.toString(16).padStart(8, '0')).join('');
}

function getElementsInBetween(businessObjects: BusinessObjects, fromElementId: string, toElementId: string): string[] {
	const parent = businessObjects[fromElementId]?.$parent;

	if (parent === undefined || parent.id === toElementId) {
		return [];
	}

	return [parent.id, ...getElementsInBetween(businessObjects, parent.id, toElementId)];
}

function createScopeIds(elementIds: string[], createId: GenerateId): ScopeIdsByElement {
	return Object.fromEntries(elementIds.map((elementId) => [elementId, createId()]));
}

function generateParentScopeIds(
	state: ModificationState,
	{
		businessObjects,
		targetElementId,
		bpmnProcessId,
		createId,
	}: Required<TokenContext> & {targetElementId: string; bpmnProcessId?: string},
) {
	if (bpmnProcessId === undefined) {
		return {};
	}

	const stagedParentElementIds = new Set(
		getElementModifications(state).flatMap((modification) =>
			modification.operation === 'CANCEL_TOKEN' ? [] : Object.keys(modification.parentScopeIds),
		),
	);

	return createScopeIds(
		getElementsInBetween(businessObjects, targetElementId, bpmnProcessId).filter(
			(elementId) => !stagedParentElementIds.has(elementId),
		),
		createId,
	);
}

function createAddTokenModification(
	state: ModificationState,
	{businessObjects, elementId, bpmnProcessId, createId = generateId}: AddTokenInput,
): AddTokenModification {
	return {
		operation: 'ADD_TOKEN',
		scopeId: createId(),
		element: {id: elementId, name: getElementName({businessObjects, elementId})},
		affectedTokenCount: 1,
		visibleAffectedTokenCount: 1,
		parentScopeIds: generateParentScopeIds(state, {
			businessObjects,
			targetElementId: elementId,
			bpmnProcessId,
			createId,
		}),
	};
}

function createCancelTokenModification({
	businessObjects,
	elementId,
	...counts
}: CancelTokenInput): CancelTokenModification {
	return {
		operation: 'CANCEL_TOKEN',
		element: {id: elementId, name: getElementName({businessObjects, elementId})},
		...counts,
	};
}

function createFinishAddingTokenAction(
	{sourceElementIdForAddOperation: elementId}: ModificationState,
	{businessObjects, ancestorElementId, ancestorElementInstanceKey, createId = generateId}: FinishAddingTokenInput,
): Extract<ModificationAction, {type: 'finishAddingToken'}> {
	if (ancestorElementId === undefined || ancestorElementInstanceKey === undefined || elementId === null) {
		return {type: 'finishAddingToken'};
	}

	return {
		type: 'finishAddingToken',
		modification: {
			operation: 'ADD_TOKEN',
			scopeId: createId(),
			element: {id: elementId, name: getElementName({businessObjects, elementId})},
			affectedTokenCount: 1,
			visibleAffectedTokenCount: 1,
			ancestorElement: {instanceKey: ancestorElementInstanceKey, elementId: ancestorElementId},
			parentScopeIds: createScopeIds(getElementsInBetween(businessObjects, elementId, ancestorElementId), createId),
		},
	};
}

function createFinishMovingTokenAction(
	state: ModificationState,
	{targetElementId, ancestorScopeType, createId = generateId, ...input}: FinishMovingTokenInput,
): Extract<ModificationAction, {type: 'finishMovingToken'}> {
	const {businessObjects, affectedTokenCount, visibleAffectedTokenCount, bpmnProcessId} = input;
	const {sourceElementIdForMoveOperation: elementId, sourceElementInstanceKeyForMoveOperation: elementInstanceKey} =
		state;

	if (targetElementId === undefined || elementId === null) {
		return {type: 'finishMovingToken'};
	}

	const isMoveAll = elementInstanceKey === null;
	const newScopeCount = isMoveAll && !isMultiInstance(businessObjects[elementId]) ? affectedTokenCount : 1;

	return {
		type: 'finishMovingToken',
		modification: {
			operation: 'MOVE_TOKEN',
			element: {id: elementId, name: getElementName({businessObjects, elementId})},
			elementInstanceKey: elementInstanceKey ?? undefined,
			targetElement: {id: targetElementId, name: getElementName({businessObjects, elementId: targetElementId})},
			affectedTokenCount,
			visibleAffectedTokenCount,
			scopeIds: Array.from({length: newScopeCount}, () => createId()),
			parentScopeIds: generateParentScopeIds(state, {businessObjects, targetElementId, bpmnProcessId, createId}),
			ancestorScopeType,
		},
	};
}

export {
	createAddTokenModification,
	createCancelTokenModification,
	createFinishAddingTokenAction,
	createFinishMovingTokenAction,
};
