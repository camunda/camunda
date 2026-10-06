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
import type {ProcessInstanceSearch} from './processInstanceSearch';

function ProcessInstancePageProvider({
	processInstanceId,
	processInstance,
	search,
	children,
}: {
	processInstanceId: string;
	processInstance: ProcessInstance;
	search: ProcessInstanceSearch;
	children: React.ReactNode;
}) {
	const history = useHistoryController(processInstance);
	const selection = useResolvedSelection(processInstanceId, search, history.handleForbidden);
	return (
		<ProcessInstanceContext value={{processInstanceId, processInstance, search, selection: search}}>
			<ElementSelectionContext value={selection}>
				<InstanceHistoryContext value={history}>{children}</InstanceHistoryContext>
			</ElementSelectionContext>
		</ProcessInstanceContext>
	);
}

export {ProcessInstancePageProvider};
