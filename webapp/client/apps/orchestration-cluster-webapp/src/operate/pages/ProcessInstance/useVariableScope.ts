/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {getElementName} from '#/operate/pages/Processes/getElementName';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {
	getElementModifications,
	getScopeMapForModification,
	hasPendingAddOrMoveModification,
} from './modifications/modificationSelectors';

function useVariableScope() {
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {state, isModificationModeEnabled} = useProcessInstanceModification();
	const {
		hasSelection,
		selectedElementId,
		selectedElementInstanceKey,
		resolvedElementInstance,
		selectedElementInstanceCount,
		isSelectedInstancePlaceholder,
	} = useProcessInstanceElementSelection();
	const {data: diagram} = useDiagramXml(isModificationModeEnabled ? processInstance.processDefinitionKey : undefined);
	const plannedScopes = new Map(
		getElementModifications(state).flatMap((modification) => Object.entries(getScopeMapForModification(modification))),
	);
	const selectedElementScopeIds = [...plannedScopes]
		.filter(([, elementId]) => elementId === selectedElementId)
		.map(([scopeId]) => scopeId);
	const newTokenCount = selectedElementScopeIds.length;
	const plannedScopeId =
		state.status === 'enabled' && newTokenCount === 1 ? (selectedElementScopeIds[0] ?? null) : null;
	const instanceCount = selectedElementInstanceCount ?? 0;
	const isPlaceholder =
		isSelectedInstancePlaceholder ||
		(selectedElementInstanceKey === null && selectedElementInstanceCount === 0 && newTokenCount === 1);
	const hasNoContent =
		hasSelection && selectedElementInstanceKey === null && selectedElementInstanceCount === 0 && newTokenCount === 0;
	const hasMultipleInstances =
		selectedElementInstanceKey === null &&
		((instanceCount > 0 && selectedElementInstanceCount !== null && resolvedElementInstance === null) ||
			(instanceCount === 1 && newTokenCount >= 1) ||
			newTokenCount > 1);
	const scopeKey = hasSelection
		? (selectedElementInstanceKey ?? resolvedElementInstance?.elementInstanceKey ?? plannedScopeId)
		: processInstanceId;
	const isPlannedScope = scopeKey !== null && plannedScopes.has(scopeKey);
	const isVariableModificationAllowed =
		isModificationModeEnabled && (hasSelection ? isPlaceholder : hasPendingAddOrMoveModification(state));

	return {
		scopeKey,
		isPlaceholder,
		hasNoContent,
		hasMultipleInstances,
		canQuery: scopeKey !== null && !isPlaceholder && !isPlannedScope && !hasMultipleInstances,
		isVariableModificationAllowed,
		elementName: hasSelection
			? getElementName({businessObjects: diagram?.businessObjects, elementId: selectedElementId ?? undefined})
			: (processInstance.processDefinitionName ?? ''),
	};
}

export {useVariableScope};
