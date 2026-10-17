/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {getFlowElementIds, isMultiInstance} from '#/operate/shared/utils/elements';
import type {
	ElementModificationPayload,
	Modification,
	ModificationState,
	VariableModificationPayload,
	VariableOperation,
} from './modificationReducer';

type ElementTokenCounts = {
	newTokens: number;
	cancelledTokens: number;
	cancelledChildTokens: number;
	visibleCancelledTokens: number;
	areAllTokensCanceled: boolean;
};

type ModificationsByElement = {[elementId: string]: ElementTokenCounts};

type RunningInstanceCounts = {
	businessObjects?: BusinessObjects;
	totalRunningInstancesByElement?: {[elementId: string]: number};
	totalRunningInstancesVisibleByElement?: {[elementId: string]: number};
};

type ElementIdsByScope = {[scopeId: string]: string};

type ElementStatistic = {elementId: string; active: number; incidents: number};

type PendingCancelOrMoveInput = {
	elementId: string;
	elementInstanceKey?: string;
	modificationsByElement?: ModificationsByElement;
};

const EMPTY_TOKEN_COUNTS: ElementTokenCounts = {
	newTokens: 0,
	cancelledTokens: 0,
	cancelledChildTokens: 0,
	visibleCancelledTokens: 0,
	areAllTokensCanceled: false,
};

function isModificationModeEnabled({status}: ModificationState) {
	return status !== 'disabled' && status !== 'applying-modifications';
}

function isMoveAllOperation({status, sourceElementInstanceKeyForMoveOperation}: ModificationState) {
	return status === 'moving-token' && sourceElementInstanceKeyForMoveOperation === null;
}

function getLastModification({modifications}: ModificationState): Modification | undefined {
	return modifications.at(-1);
}

function getElementModifications({modifications}: ModificationState) {
	return modifications.flatMap(({type, payload}) => (type === 'token' ? [payload] : []));
}

function getVariableModifications({modifications}: ModificationState) {
	const latestModifications = new Map<string, VariableModificationPayload>();

	for (const {type, payload} of modifications) {
		if (type === 'variable') {
			latestModifications.set(JSON.stringify([payload.scopeId, payload.id]), payload);
		}
	}

	return [...latestModifications.values()];
}

function getLastVariableModification(
	state: ModificationState,
	scopeId: string | null,
	id: string,
	operation: VariableOperation,
) {
	return getVariableModifications(state).find(
		(modification) =>
			modification.operation === operation && modification.scopeId === scopeId && modification.id === id,
	);
}

function getScopeMapForModification(modification: ElementModificationPayload): ElementIdsByScope {
	if (modification.operation === 'CANCEL_TOKEN') {
		return {};
	}

	const [scopeIds, targetElementId] =
		modification.operation === 'ADD_TOKEN'
			? [[modification.scopeId], modification.element.id]
			: [modification.scopeIds, modification.targetElement.id];

	return Object.fromEntries([
		...Object.entries(modification.parentScopeIds).map(([elementId, parentScopeId]) => [parentScopeId, elementId]),
		...scopeIds.map((scopeId) => [scopeId, targetElementId]),
	]);
}

function hasPendingAddOrMoveModification(state: ModificationState) {
	return getElementModifications(state).some(({operation}) => operation !== 'CANCEL_TOKEN');
}

function hasPendingCancelOrMoveModification(
	state: ModificationState,
	{elementId, elementInstanceKey, modificationsByElement}: PendingCancelOrMoveInput,
) {
	if (elementInstanceKey !== undefined) {
		return getElementModifications(state).some(
			(modification) =>
				modification.operation !== 'ADD_TOKEN' && modification.elementInstanceKey === elementInstanceKey,
		);
	}

	return (modificationsByElement?.[elementId]?.cancelledTokens ?? 0) > 0;
}

function hasOrphanedVariableModifications(state: ModificationState, processInstanceKey: string) {
	const variableModifications = getVariableModifications(state);
	const scopeCreatingModifications = getElementModifications(state).filter(
		({operation}) => operation !== 'CANCEL_TOKEN',
	);
	const pendingScopeIds = new Set(
		scopeCreatingModifications.flatMap((modification) => Object.keys(getScopeMapForModification(modification))),
	);

	return variableModifications.some(({scopeId}) =>
		scopeId === processInstanceKey ? scopeCreatingModifications.length === 0 : !pendingScopeIds.has(scopeId),
	);
}

function getModificationsByElement(
	state: ModificationState,
	{
		businessObjects,
		totalRunningInstancesByElement = {},
		totalRunningInstancesVisibleByElement = {},
	}: RunningInstanceCounts = {},
): ModificationsByElement {
	const countsByElement = new Map<string, ElementTokenCounts>();

	for (const modification of getElementModifications(state)) {
		const {element, affectedTokenCount, visibleAffectedTokenCount} = modification;
		const sourceCounts = countsByElement.get(element.id) ?? {...EMPTY_TOKEN_COUNTS};

		if (modification.operation === 'ADD_TOKEN') {
			sourceCounts.newTokens += affectedTokenCount;
			countsByElement.set(element.id, sourceCounts);
			continue;
		}

		if (sourceCounts.areAllTokensCanceled) {
			continue;
		}

		if (modification.elementInstanceKey === undefined) {
			sourceCounts.cancelledTokens = affectedTokenCount;
			sourceCounts.visibleCancelledTokens = visibleAffectedTokenCount;
		} else {
			sourceCounts.cancelledTokens += affectedTokenCount;
			sourceCounts.visibleCancelledTokens += visibleAffectedTokenCount;
		}

		sourceCounts.areAllTokensCanceled =
			sourceCounts.cancelledTokens === (totalRunningInstancesByElement[element.id] ?? 0);

		if (modification.operation === 'MOVE_TOKEN') {
			const targetCounts = countsByElement.get(modification.targetElement.id) ?? {...EMPTY_TOKEN_COUNTS};
			targetCounts.newTokens += isMultiInstance(businessObjects?.[element.id]) ? 1 : affectedTokenCount;
			countsByElement.set(modification.targetElement.id, targetCounts);
		}

		if (modification.operation === 'CANCEL_TOKEN' && sourceCounts.areAllTokensCanceled) {
			sourceCounts.cancelledChildTokens = getFlowElementIds(businessObjects?.[element.id]).reduce(
				(cancelledChildTokens, childElementId) => {
					const childCounts = countsByElement.get(childElementId) ?? {...EMPTY_TOKEN_COUNTS};
					const cancelledTokens = totalRunningInstancesByElement[childElementId];
					const visibleCancelledTokens = totalRunningInstancesVisibleByElement[childElementId];

					if (cancelledTokens) {
						childCounts.cancelledTokens = cancelledTokens;
					}

					if (visibleCancelledTokens) {
						childCounts.visibleCancelledTokens = visibleCancelledTokens;
					}

					childCounts.areAllTokensCanceled = true;
					countsByElement.set(childElementId, childCounts);

					return cancelledChildTokens + childCounts.visibleCancelledTokens;
				},
				0,
			);
		}

		countsByElement.set(element.id, sourceCounts);
	}

	return Object.fromEntries(countsByElement);
}

function willAllElementsBeCanceled(
	state: ModificationState,
	statistics: ElementStatistic[] | undefined,
	modificationsByElement: ModificationsByElement,
) {
	if (hasPendingAddOrMoveModification(state)) {
		return false;
	}

	return (
		statistics?.every(
			({elementId, active, incidents}) =>
				(active === 0 && incidents === 0) || modificationsByElement[elementId]?.areAllTokensCanceled === true,
		) ?? false
	);
}

export {
	getElementModifications,
	getLastModification,
	getLastVariableModification,
	getModificationsByElement,
	getScopeMapForModification,
	getVariableModifications,
	hasOrphanedVariableModifications,
	hasPendingAddOrMoveModification,
	hasPendingCancelOrMoveModification,
	isModificationModeEnabled,
	isMoveAllOperation,
	willAllElementsBeCanceled,
};
export type {ElementIdsByScope};
