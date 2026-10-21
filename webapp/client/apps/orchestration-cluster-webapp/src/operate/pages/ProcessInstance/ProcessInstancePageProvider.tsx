/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {ProcessInstanceContext} from './useProcessInstancePage';
import {ElementSelectionContext, useResolvedSelection} from './useProcessInstanceElementSelection';
import {InstanceHistoryContext, useHistoryController} from './useInstanceHistory';
import {IncidentsFilterContext, useIncidentsFilterState} from './useIncidentsFilter';
import {isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {getProcessInstanceSelection, type ProcessInstanceSearch} from './processInstanceSearch';
import {ProcessInstanceModificationContext, type ModificationController} from './useProcessInstanceModification';
import {getElementModifications} from './modifications/modificationSelectors';
import type {ElementModificationPayload} from './modifications/modificationReducer';

function getNewScopeIds(elementModifications: ElementModificationPayload[]) {
	return elementModifications.flatMap((modification) => {
		switch (modification.operation) {
			case 'ADD_TOKEN':
				return [...Object.values(modification.parentScopeIds), modification.scopeId];
			case 'MOVE_TOKEN':
				return [...Object.values(modification.parentScopeIds), ...modification.scopeIds];
			default:
				return [];
		}
	});
}

function ProcessInstancePageProvider({
	processInstanceId,
	processInstance,
	search,
	modification,
	children,
}: {
	processInstanceId: string;
	processInstance: ProcessInstance;
	search: ProcessInstanceSearch;
	modification?: ModificationController;
	children: React.ReactNode;
}) {
	const history = useHistoryController(processInstance);
	const selection = useResolvedSelection(
		processInstanceId,
		search,
		history.handleForbidden,
		isInstanceRunning(processInstance) && !modification?.isModificationModeEnabled,
	);
	const incidentsFilter = useIncidentsFilterState(processInstanceId);
	const modificationState = modification?.state;
	const elementModificationCount = useRef(0);
	const {selectedElementId, selectedElementInstanceKey, resolvedElementInstance, clearSelection} = selection;
	useEffect(() => {
		const elementModifications = modificationState === undefined ? [] : getElementModifications(modificationState);
		const previousCount = elementModificationCount.current;
		elementModificationCount.current = elementModifications.length;
		if (selectedElementId === null || elementModifications.length >= previousCount) {
			return;
		}
		const elementInstanceKey = resolvedElementInstance?.elementInstanceKey ?? selectedElementInstanceKey;
		if (elementInstanceKey !== null && !getNewScopeIds(elementModifications).includes(elementInstanceKey)) {
			clearSelection();
		}
	}, [modificationState, selectedElementId, selectedElementInstanceKey, resolvedElementInstance, clearSelection]);
	return (
		<ProcessInstanceContext
			value={{processInstanceId, processInstance, search, selection: getProcessInstanceSelection(search)}}
		>
			<ElementSelectionContext value={selection}>
				<InstanceHistoryContext value={history}>
					<IncidentsFilterContext value={incidentsFilter}>
						{modification === undefined ? (
							children
						) : (
							<ProcessInstanceModificationContext
								value={{
									...modification,
									enterModificationMode: () => {
										modification.dispatch({type: 'enableModificationMode'});
										selection.clearSelection();
									},
									discardModifications: () => {
										modification.dispatch({type: 'reset'});
										selection.clearSelection();
									},
								}}
							>
								{children}
							</ProcessInstanceModificationContext>
						)}
					</IncidentsFilterContext>
				</InstanceHistoryContext>
			</ElementSelectionContext>
		</ProcessInstanceContext>
	);
}

export {ProcessInstancePageProvider};
