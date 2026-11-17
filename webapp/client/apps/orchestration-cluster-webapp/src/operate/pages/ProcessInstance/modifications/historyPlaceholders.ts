/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject, BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementModificationPayload, ModificationState} from './modificationReducer';
import {getElementModifications} from './modificationSelectors';

type HistoryPlaceholder = {
	elementId: string;
	elementInstanceKey: string;
	parentElementId?: string;
	parentInstanceId?: string;
};

type ScopeCreatingModification = Exclude<ElementModificationPayload, {operation: 'CANCEL_TOKEN'}>;

type PlaceholderContext = {
	processDefinitionId?: string;
	processInstanceKey?: string;
};

function getParentInstanceId(
	modification: ScopeCreatingModification,
	{processDefinitionId, processInstanceKey}: PlaceholderContext,
	parentElementId?: string,
) {
	if (parentElementId === undefined) {
		return undefined;
	}

	if (parentElementId === processDefinitionId) {
		return processInstanceKey;
	}

	if (modification.ancestorElement?.elementId === parentElementId) {
		return modification.ancestorElement.instanceKey;
	}

	return modification.parentScopeIds[parentElementId];
}

function getParentPlaceholders(
	modification: ScopeCreatingModification,
	context: PlaceholderContext,
	element?: BusinessObject,
): HistoryPlaceholder[] {
	const scopeId = element === undefined ? undefined : modification.parentScopeIds[element.id];

	if (element === undefined || scopeId === undefined) {
		return [];
	}

	const parentElementId = element.$parent?.id;

	return [
		...getParentPlaceholders(modification, context, element.$parent),
		{
			elementId: element.id,
			elementInstanceKey: scopeId,
			parentElementId,
			parentInstanceId: getParentInstanceId(modification, context, parentElementId),
		},
	];
}

function getModificationPlaceholders(
	state: ModificationState,
	businessObjects: BusinessObjects,
	processDefinitionId: string,
	processInstanceKey: string,
): HistoryPlaceholder[] {
	const placeholders = getElementModifications(state).flatMap((modification) => {
		if (modification.operation === 'CANCEL_TOKEN') {
			return [];
		}

		const element =
			businessObjects[
				modification.operation === 'MOVE_TOKEN' ? modification.targetElement.id : modification.element.id
			];
		const parentElementId = element?.$parent?.id;

		if (element === undefined || parentElementId === undefined) {
			return [];
		}

		const context = {processDefinitionId, processInstanceKey};
		const scopeIds = modification.operation === 'ADD_TOKEN' ? [modification.scopeId] : modification.scopeIds;
		const parentInstanceId = getParentInstanceId(modification, context, parentElementId);

		return [
			...scopeIds.map((scopeId) => ({
				elementId: element.id,
				elementInstanceKey: scopeId,
				parentElementId,
				parentInstanceId,
			})),
			...getParentPlaceholders(modification, context, element.$parent),
		];
	});

	return placeholders.filter(
		(placeholder, index) =>
			placeholders.findIndex(({elementInstanceKey}) => elementInstanceKey === placeholder.elementInstanceKey) === index,
	);
}

function getChildPlaceholders(
	placeholders: HistoryPlaceholder[],
	scopeKey: string,
	elementId: string,
	isRunningScope: boolean,
) {
	return placeholders.filter(
		({parentInstanceId, parentElementId}) =>
			parentInstanceId === scopeKey ||
			(isRunningScope && parentInstanceId === undefined && parentElementId === elementId),
	);
}

function hasChildPlaceholders(placeholders: HistoryPlaceholder[], scopeKey: string) {
	return placeholders.some(({parentInstanceId}) => parentInstanceId === scopeKey);
}

type LoadedScope = {key: string; items: {elementId: string; elementInstanceKey: string}[]};

function getLoadedInstanceAncestors(
	scopes: LoadedScope[],
	instanceKey: string,
	processInstanceKey: string,
	processDefinitionId: string,
) {
	const ancestors: {elementId: string; instanceKey: string}[] = [];
	for (let key = instanceKey; key !== processInstanceKey;) {
		const parentKey = scopes.find(({items}) => items.some(({elementInstanceKey}) => elementInstanceKey === key))?.key;
		if (parentKey === undefined) {
			return [];
		}
		if (parentKey !== processInstanceKey) {
			const parent = scopes
				.flatMap(({items}) => items)
				.find(({elementInstanceKey}) => elementInstanceKey === parentKey);
			if (parent === undefined) {
				return [];
			}
			if (!ancestors.some(({elementId}) => elementId === parent.elementId)) {
				ancestors.push({elementId: parent.elementId, instanceKey: parentKey});
			}
		}
		key = parentKey;
	}
	return [...ancestors, {elementId: processDefinitionId, instanceKey: processInstanceKey}];
}

export {getChildPlaceholders, getLoadedInstanceAncestors, getModificationPlaceholders, hasChildPlaceholders};
export type {HistoryPlaceholder};
