/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {ProcessInstanceContext} from './useProcessInstancePage';
import {ElementSelectionContext, useResolvedSelection} from './useProcessInstanceElementSelection';
import {InstanceHistoryContext, useHistoryController} from './useInstanceHistory';
import {getProcessInstanceSelection, type ProcessInstanceSearch} from './processInstanceSearch';
import {ProcessInstanceModificationContext, type ModificationController} from './useProcessInstanceModification';

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
	const selection = useResolvedSelection(processInstanceId, search, history.handleForbidden);
	return (
		<ProcessInstanceContext
			value={{processInstanceId, processInstance, search, selection: getProcessInstanceSelection(search)}}
		>
			<ElementSelectionContext value={selection}>
				<InstanceHistoryContext value={history}>
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
				</InstanceHistoryContext>
			</ElementSelectionContext>
		</ProcessInstanceContext>
	);
}

export {ProcessInstancePageProvider};
