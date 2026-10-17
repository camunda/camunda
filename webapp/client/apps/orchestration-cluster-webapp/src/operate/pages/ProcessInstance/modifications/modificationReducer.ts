/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getLastVariableModification} from './modificationSelectors';

type AncestorScopeType = 'inferred' | 'sourceParent';

type ModificationElement = {id: string; name: string};

type ScopeIdsByElement = {[elementId: string]: string};

type TokenModificationBase = {
	element: ModificationElement;
	affectedTokenCount: number;
	visibleAffectedTokenCount: number;
};

type AddTokenModification = TokenModificationBase & {
	operation: 'ADD_TOKEN';
	scopeId: string;
	ancestorElement?: {instanceKey: string; elementId: string};
	parentScopeIds: ScopeIdsByElement;
};

type CancelTokenModification = TokenModificationBase & {operation: 'CANCEL_TOKEN'; elementInstanceKey?: string};

type MoveTokenModification = TokenModificationBase & {
	operation: 'MOVE_TOKEN';
	elementInstanceKey?: string;
	targetElement: ModificationElement;
	scopeIds: string[];
	parentScopeIds: ScopeIdsByElement;
	ancestorScopeType?: AncestorScopeType;
};

type ElementModificationPayload = AddTokenModification | CancelTokenModification | MoveTokenModification;

type VariableOperation = 'ADD_VARIABLE' | 'EDIT_VARIABLE';

type VariableModificationPayload = {
	operation: VariableOperation;
	id: string;
	scopeId: string;
	elementName: string;
	name: string;
	oldValue?: string;
	newValue: string;
};

type Modification =
	{type: 'token'; payload: ElementModificationPayload} | {type: 'variable'; payload: VariableModificationPayload};

type RemovedModificationSource = 'variables' | 'summaryModal' | 'footer';

type ModificationStatus = 'disabled' | 'enabled' | 'adding-token' | 'moving-token' | 'applying-modifications';

type ModificationState = {
	status: ModificationStatus;
	modifications: Modification[];
	lastRemovedModification?: {modification: Modification | undefined; source: RemovedModificationSource};
	sourceElementIdForMoveOperation: string | null;
	sourceElementInstanceKeyForMoveOperation: string | null;
	sourceElementIdForAddOperation: string | null;
};

type ModificationAction =
	| {type: 'enableModificationMode'}
	| {type: 'disableModificationMode'}
	| {type: 'startApplyingModifications'}
	| {type: 'reset'}
	| {type: 'startAddingToken'; elementId: string}
	| {type: 'finishAddingToken'; modification?: AddTokenModification}
	| {type: 'startMovingToken'; elementId: string; elementInstanceKey?: string}
	| {type: 'finishMovingToken'; modification?: MoveTokenModification}
	| {type: 'addModification'; modification: Modification}
	| {type: 'removeLastModification'}
	| {type: 'removeElementModification'; modification: ElementModificationPayload}
	| {
			type: 'removeVariableModification';
			scopeId: string;
			id: string;
			operation: VariableOperation;
			source: RemovedModificationSource;
	  };

const initialModificationState: ModificationState = {
	status: 'disabled',
	modifications: [],
	sourceElementIdForMoveOperation: null,
	sourceElementInstanceKeyForMoveOperation: null,
	sourceElementIdForAddOperation: null,
};

function matchesElementModification({type, payload}: Modification, target: ElementModificationPayload) {
	if (type !== 'token' || payload.element.id !== target.element.id || payload.operation !== target.operation) {
		return false;
	}

	if (payload.operation === 'ADD_TOKEN' || target.operation === 'ADD_TOKEN') {
		return payload.operation === 'ADD_TOKEN' && target.operation === 'ADD_TOKEN' && payload.scopeId === target.scopeId;
	}

	return payload.elementInstanceKey === target.elementInstanceKey;
}

function appendTokenModification(modifications: Modification[], payload?: ElementModificationPayload) {
	return payload === undefined ? modifications : [...modifications, {type: 'token' as const, payload}];
}

function modificationReducer(state: ModificationState, action: ModificationAction): ModificationState {
	switch (action.type) {
		case 'enableModificationMode':
			return {...state, status: 'enabled'};
		case 'disableModificationMode':
			return {...state, status: 'disabled'};
		case 'startApplyingModifications':
			return {...state, status: 'applying-modifications'};
		case 'reset':
			return initialModificationState;
		case 'startAddingToken':
			return {...state, status: 'adding-token', sourceElementIdForAddOperation: action.elementId};
		case 'finishAddingToken':
			return {
				...state,
				status: 'enabled',
				sourceElementIdForAddOperation: null,
				modifications: appendTokenModification(state.modifications, action.modification),
			};
		case 'startMovingToken':
			return {
				...state,
				status: 'moving-token',
				sourceElementIdForMoveOperation: action.elementId,
				sourceElementInstanceKeyForMoveOperation: action.elementInstanceKey ?? null,
			};
		case 'finishMovingToken':
			return {
				...state,
				status: 'enabled',
				sourceElementIdForMoveOperation: null,
				sourceElementInstanceKeyForMoveOperation: null,
				modifications: appendTokenModification(state.modifications, action.modification),
			};
		case 'addModification':
			return {...state, modifications: [...state.modifications, action.modification]};
		case 'removeLastModification':
			return {
				...state,
				modifications: state.modifications.slice(0, -1),
				lastRemovedModification: {modification: state.modifications.at(-1), source: 'footer'},
			};
		case 'removeElementModification':
			return {
				...state,
				modifications: state.modifications.filter(
					(modification) => !matchesElementModification(modification, action.modification),
				),
			};
		case 'removeVariableModification': {
			const {scopeId, id, operation, source} = action;
			const lastModification = getLastVariableModification(state, scopeId, id, operation);

			if (lastModification === undefined) {
				return state;
			}

			return {
				...state,
				modifications: state.modifications.filter(
					({type, payload}) =>
						!(
							type === 'variable' &&
							payload.scopeId === scopeId &&
							payload.id === id &&
							payload.operation === operation
						),
				),
				lastRemovedModification: {modification: {type: 'variable', payload: lastModification}, source},
			};
		}
		default:
			return state;
	}
}

export {initialModificationState, modificationReducer};
export type {
	AddTokenModification,
	AncestorScopeType,
	CancelTokenModification,
	ElementModificationPayload,
	Modification,
	ModificationAction,
	ModificationState,
	MoveTokenModification,
	ScopeIdsByElement,
	VariableModificationPayload,
	VariableOperation,
};
