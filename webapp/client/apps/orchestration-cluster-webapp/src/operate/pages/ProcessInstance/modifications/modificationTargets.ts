/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject, BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {hasType, isMultiInstance} from '#/operate/shared/utils/elements';
import {isMoveTarget} from '#/operate/pages/Processes/batchModificationTargets';
import type {AncestorScopeType, ModificationState} from './modificationReducer';
import {
	getElementModifications,
	hasPendingCancelOrMoveModification,
	isMoveAllOperation,
	type getModificationsByElement,
} from './modificationSelectors';

type RunningInstancesByElement = {[elementId: string]: number};

type ElementStatistic = {elementId: string; active: number; incidents: number};

type ModificationOption = 'add' | 'cancel-all' | 'cancel-instance' | 'move-all' | 'move-instance';

type AvailableModificationsInput = {
	state: ModificationState;
	businessObjects?: BusinessObjects;
	statistics?: ElementStatistic[];
	runningInstancesByElement: RunningInstancesByElement;
	modificationsByElement: ReturnType<typeof getModificationsByElement>;
	elementId: string;
	selectedElementInstanceKey: string | null;
	runningElementInstanceCount: number;
	isMultiInstanceBody: boolean;
	isSingleRunningInstanceResolved: boolean;
};

function isSubProcess(businessObject?: BusinessObject) {
	return businessObject !== undefined && hasType({businessObject, types: ['bpmn:SubProcess']});
}

function isAdHocSubProcess(businessObject?: BusinessObject) {
	return businessObject !== undefined && hasType({businessObject, types: ['bpmn:AdHocSubProcess']});
}

function getRunningInstancesByElement(
	statistics: ElementStatistic[] = [],
	businessObjects: BusinessObjects = {},
): RunningInstancesByElement {
	return Object.fromEntries(
		statistics
			.filter(({elementId}) => businessObjects[elementId] !== undefined)
			.map(({elementId, active, incidents}) => [elementId, active + incidents]),
	);
}

type ScopeChainEntry = {count: number; isMultiInstance: boolean};

function getScopeChain(
	parentElement: BusinessObject | undefined,
	runningInstancesByElement: RunningInstancesByElement,
): ScopeChainEntry[] {
	const chain: ScopeChainEntry[] = [];
	for (let parent = parentElement; parent !== undefined; parent = parent.$parent) {
		chain.push({count: runningInstancesByElement[parent.id] ?? 0, isMultiInstance: isMultiInstance(parent)});
		if (!isSubProcess(parent.$parent) && !isAdHocSubProcess(parent.$parent)) {
			break;
		}
	}
	return chain;
}

function hasMultipleScopes(
	parentElement: BusinessObject | undefined,
	runningInstancesByElement: RunningInstancesByElement,
) {
	return getScopeChain(parentElement, runningInstancesByElement).some(({count}) => count > 1);
}

function getTargetScopes(element: BusinessObject | undefined, runningInstancesByElement: RunningInstancesByElement) {
	const chain = getScopeChain(element?.$parent, runningInstancesByElement);
	const boundaryIndex = chain.findIndex(({isMultiInstance}) => isMultiInstance);
	const boundaryChain = boundaryIndex === -1 ? chain : chain.slice(0, boundaryIndex + 1);
	const hasMultipleScopesWithinBoundary = boundaryChain.some(({count}) => count > 1);

	return {
		isEffectivelySingleScope:
			getFirstMultiInstanceParent(element) === undefined ||
			(boundaryChain.some(({count}) => count === 1) && !hasMultipleScopesWithinBoundary),
		hasMultipleScopesWithinBoundary,
		requiresParentScopeSelection: chain.some(({count}) => count > 1),
	};
}

function getFirstMultiInstanceParent(businessObject?: BusinessObject): BusinessObject | undefined {
	if (!businessObject) {
		return undefined;
	}

	if (isMultiInstance(businessObject.$parent)) {
		return businessObject.$parent;
	}

	return getFirstMultiInstanceParent(businessObject.$parent);
}

function isAncestorWithinMultiInstanceBoundary(
	businessObjects: BusinessObjects,
	elementId: string,
	ancestorElementId: string,
) {
	for (let parent = businessObjects[elementId]?.$parent; parent !== undefined; parent = parent.$parent) {
		if (parent.id === ancestorElementId) {
			return true;
		}
		if (isMultiInstance(parent)) {
			return false;
		}
	}
	return false;
}

function isInsideCanceledScope(
	businessObjects: BusinessObjects | undefined,
	elementId: string,
	modificationsByElement: ReturnType<typeof getModificationsByElement>,
) {
	for (let parent = businessObjects?.[elementId]?.$parent; parent !== undefined; parent = parent.$parent) {
		if (modificationsByElement[parent.id]?.areAllTokensCanceled === true) {
			return true;
		}
	}
	return false;
}

function canBeAddedTokenAncestor(
	businessObjects: BusinessObjects,
	sourceElementId: string,
	instance: {elementId: string; state: string; type: string},
) {
	return (
		instance.state === 'ACTIVE' &&
		instance.type !== 'MULTI_INSTANCE_BODY' &&
		isAncestorWithinMultiInstanceBoundary(businessObjects, sourceElementId, instance.elementId)
	);
}

function hasActivationInCanceledScope(
	state: ModificationState,
	businessObjects: BusinessObjects | undefined,
	modificationsByElement: ReturnType<typeof getModificationsByElement>,
) {
	const elementModifications = getElementModifications(state);
	const canceledInstanceKeys = new Set(
		elementModifications.flatMap((modification) =>
			modification.operation === 'CANCEL_TOKEN' && modification.elementInstanceKey !== undefined
				? [modification.elementInstanceKey]
				: [],
		),
	);

	return elementModifications.some((modification) => {
		if (modification.operation === 'CANCEL_TOKEN') {
			return false;
		}
		const {ancestorElement} = modification;
		if (ancestorElement !== undefined) {
			return (
				canceledInstanceKeys.has(ancestorElement.instanceKey) ||
				modificationsByElement[ancestorElement.elementId]?.areAllTokensCanceled === true ||
				isInsideCanceledScope(businessObjects, ancestorElement.elementId, modificationsByElement)
			);
		}
		const targetElementId =
			modification.operation === 'MOVE_TOKEN' ? modification.targetElement.id : modification.element.id;
		return isInsideCanceledScope(businessObjects, targetElementId, modificationsByElement);
	});
}

function withoutMovesFromCanceledScopes(
	state: ModificationState,
	businessObjects: BusinessObjects | undefined,
	modificationsByElement: ReturnType<typeof getModificationsByElement>,
): ModificationState {
	const modifications = state.modifications.filter(
		({type, payload}) =>
			!(
				type === 'token' &&
				payload.operation === 'MOVE_TOKEN' &&
				payload.elementInstanceKey === undefined &&
				isInsideCanceledScope(businessObjects, payload.element.id, modificationsByElement)
			),
	);
	return modifications.length === state.modifications.length ? state : {...state, modifications};
}

function getAncestorScopeType(
	businessObjects: BusinessObjects,
	sourceElementId: string,
	targetElementId: string,
	runningInstancesByElement: RunningInstancesByElement,
): AncestorScopeType | undefined {
	if (!hasMultipleScopes(businessObjects[targetElementId]?.$parent, runningInstancesByElement)) {
		return undefined;
	}

	const sourceParent = businessObjects[sourceElementId]?.$parent;
	const targetParent = businessObjects[targetElementId]?.$parent;
	const isInSameRunningScope =
		!isMultiInstance(businessObjects[sourceElementId]) &&
		sourceParent !== undefined &&
		targetParent !== undefined &&
		sourceParent.id === targetParent.id &&
		(runningInstancesByElement[sourceParent.id] ?? 0) > 0;

	return isInSameRunningScope ? 'sourceParent' : 'inferred';
}

function getAppendableElements(
	state: ModificationState,
	businessObjects: BusinessObjects = {},
	runningInstancesByElement: RunningInstancesByElement,
) {
	const elements = Object.values(businessObjects);
	const sourceElement = elements.find(({id}) => id === state.sourceElementIdForMoveOperation);

	return elements
		.filter((element) => {
			if (!isMoveTarget(element)) {
				return false;
			}

			const scopes = getTargetScopes(element, runningInstancesByElement);

			if (state.status !== 'moving-token') {
				return scopes.isEffectivelySingleScope || scopes.hasMultipleScopesWithinBoundary;
			}

			const multiInstanceParent = getFirstMultiInstanceParent(element);

			if (
				scopes.isEffectivelySingleScope &&
				(multiInstanceParent === undefined || !scopes.requiresParentScopeSelection)
			) {
				return true;
			}

			if (isMoveAllOperation(state) && !scopes.isEffectivelySingleScope) {
				return false;
			}

			return getFirstMultiInstanceParent(sourceElement) === multiInstanceParent;
		})
		.map(({id}) => id);
}

function getCancellableElements(businessObjects: BusinessObjects = {}, statistics: ElementStatistic[] = []) {
	return Object.values(businessObjects)
		.filter((element) => {
			const statistic = statistics.find(({elementId}) => elementId === element.id);
			return statistic !== undefined && (statistic.active > 0 || statistic.incidents > 0);
		})
		.map(({id}) => id);
}

function getModifiableElements(
	state: ModificationState,
	businessObjects: BusinessObjects | undefined,
	statistics: ElementStatistic[] | undefined,
	runningInstancesByElement: RunningInstancesByElement,
) {
	const appendableElements = getAppendableElements(state, businessObjects, runningInstancesByElement);

	if (state.status === 'moving-token') {
		return appendableElements.filter((elementId) => elementId !== state.sourceElementIdForMoveOperation);
	}

	return [...new Set([...appendableElements, ...getCancellableElements(businessObjects, statistics)])];
}

function canElementBeModified(
	state: ModificationState,
	businessObjects: BusinessObjects | undefined,
	statistics: ElementStatistic[] | undefined,
	runningInstancesByElement: RunningInstancesByElement,
	elementId: string,
) {
	const modifiableElements = getModifiableElements(state, businessObjects, statistics, runningInstancesByElement);
	return businessObjects?.[elementId] === undefined || modifiableElements.includes(elementId);
}

function getAvailableModifications({
	state,
	businessObjects,
	statistics,
	runningInstancesByElement,
	modificationsByElement,
	elementId,
	selectedElementInstanceKey,
	runningElementInstanceCount,
	isMultiInstanceBody,
	isSingleRunningInstanceResolved,
}: AvailableModificationsInput): ModificationOption[] {
	if (!canElementBeModified(state, businessObjects, statistics, runningInstancesByElement, elementId)) {
		return [];
	}

	const options: ModificationOption[] = [];
	const businessObject = businessObjects?.[elementId];

	if (
		getAppendableElements(state, businessObjects, runningInstancesByElement).includes(elementId) &&
		!(isMultiInstance(businessObject) && !isMultiInstanceBody) &&
		selectedElementInstanceKey === null &&
		!isInsideCanceledScope(businessObjects, elementId, modificationsByElement)
	) {
		options.push('add');
	}

	const isSingleOperationAllowed =
		isSingleRunningInstanceResolved && runningElementInstanceCount === 1 && !isSubProcess(businessObject);
	const hasPendingCancelOrMove =
		modificationsByElement[elementId]?.areAllTokensCanceled === true ||
		hasPendingCancelOrMoveModification(state, {
			elementId,
			elementInstanceKey: isSingleOperationAllowed ? (selectedElementInstanceKey ?? undefined) : undefined,
			modificationsByElement,
		});
	const canBeCanceled =
		getCancellableElements(businessObjects, statistics).includes(elementId) &&
		!hasPendingCancelOrMove &&
		runningElementInstanceCount > 0;

	if (!canBeCanceled) {
		return options;
	}

	options.push(isSingleOperationAllowed ? 'cancel-instance' : 'cancel-all');

	if (isSubProcess(businessObject)) {
		return options;
	}

	options.push(isSingleOperationAllowed ? 'move-instance' : 'move-all');

	return options;
}

export {
	canElementBeModified,
	getAncestorScopeType,
	getAvailableModifications,
	getModifiableElements,
	getRunningInstancesByElement,
	hasMultipleScopes,
	getTargetScopes,
	canBeAddedTokenAncestor,
	hasActivationInCanceledScope,
	withoutMovesFromCanceledScopes,
};
export type {RunningInstancesByElement};
