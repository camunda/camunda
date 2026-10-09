/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ModifyProcessInstanceRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import type {ModificationState} from './modificationReducer';
import {
	getElementModifications,
	getScopeMapForModification,
	getVariableModifications,
	type ElementIdsByScope,
} from './modificationSelectors';

type ActivateInstruction = NonNullable<ModifyProcessInstanceRequestBody['activateInstructions']>[number];
type MoveInstruction = NonNullable<ModifyProcessInstanceRequestBody['moveInstructions']>[number];
type TerminateInstruction = NonNullable<ModifyProcessInstanceRequestBody['terminateInstructions']>[number];
type VariableInstruction = NonNullable<ActivateInstruction['variableInstructions']>[number];

type ModificationInstructions = {
	activateInstructions: ActivateInstruction[];
	moveInstructions: MoveInstruction[];
	terminateInstructions: TerminateInstruction[];
};

type ModificationInstructionsResult =
	{status: 'ready'; instructions: ModificationInstructions} | {status: 'missing-root-variable-host'};

function getVariablesForScope(state: ModificationState, scopeId: string) {
	const variableModifications = getVariableModifications(state).filter(
		(modification) => modification.scopeId === scopeId,
	);

	if (variableModifications.length === 0) {
		return undefined;
	}

	return Object.fromEntries<unknown>(variableModifications.map(({name, newValue}) => [name, JSON.parse(newValue)]));
}

function getVariableInstructions(state: ModificationState, scopeMap: ElementIdsByScope) {
	return Object.entries(scopeMap).flatMap<VariableInstruction>(([scopeId, elementId]) => {
		const variables = getVariablesForScope(state, scopeId);
		return variables === undefined ? [] : [{variables, scopeId: elementId}];
	});
}

function buildModificationInstructions(
	state: ModificationState,
	processInstanceKey: string,
): ModificationInstructionsResult {
	const instructions: ModificationInstructions = {
		activateInstructions: [],
		moveInstructions: [],
		terminateInstructions: [],
	};

	for (const modification of getElementModifications(state)) {
		switch (modification.operation) {
			case 'CANCEL_TOKEN':
				instructions.terminateInstructions.push(
					modification.elementInstanceKey === undefined
						? {elementId: modification.element.id}
						: {elementInstanceKey: modification.elementInstanceKey},
				);
				break;
			case 'ADD_TOKEN':
				instructions.activateInstructions.push({
					elementId: modification.element.id,
					...(modification.ancestorElement !== undefined && {
						ancestorElementInstanceKey: modification.ancestorElement.instanceKey,
					}),
					variableInstructions: getVariableInstructions(state, getScopeMapForModification(modification)),
				});
				break;
			case 'MOVE_TOKEN':
				instructions.moveInstructions.push({
					sourceElementInstruction: modification.elementInstanceKey
						? {sourceType: 'byKey', sourceElementInstanceKey: modification.elementInstanceKey}
						: {sourceType: 'byId', sourceElementId: modification.element.id},
					targetElementId: modification.targetElement.id,
					...(modification.ancestorScopeType && {
						ancestorScopeInstruction: {ancestorScopeType: modification.ancestorScopeType},
					}),
					variableInstructions: getVariableInstructions(state, getScopeMapForModification(modification)),
				});
				break;
		}
	}

	const rootVariables = getVariablesForScope(state, processInstanceKey);

	if (rootVariables === undefined) {
		return {status: 'ready', instructions};
	}

	const [hostInstruction] = [...instructions.activateInstructions, ...instructions.moveInstructions];

	if (hostInstruction === undefined) {
		return {status: 'missing-root-variable-host'};
	}

	hostInstruction.variableInstructions = [...(hostInstruction.variableInstructions ?? []), {variables: rootVariables}];

	return {status: 'ready', instructions};
}

export {buildModificationInstructions};
