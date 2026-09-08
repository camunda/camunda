/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext} from 'react';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import type {ProcessInstanceSearch, ProcessInstanceSelection, ProcessInstanceTab} from './processInstanceSearch';

type ProcessInstancePageContext = {
	processInstanceId: string;
	processDefinitionKey: string;
	processInstance: ProcessInstance;
	search: ProcessInstanceSearch;
	selection: ProcessInstanceSelection;
	activeTab: ProcessInstanceTab | undefined;
	waitingCount: number;
	selectElement: (selection: Pick<ProcessInstanceSelection, 'elementId' | 'isMultiInstanceBody'>) => void;
	selectElementInstance: (selection: ProcessInstanceSelection) => void;
	clearSelection: () => void;
	setActiveTab: (tab: ProcessInstanceTab) => void;
};
const ProcessInstanceContext = createContext<ProcessInstancePageContext | null>(null);

function useProcessInstancePage() {
	const context = useContext(ProcessInstanceContext);
	if (context === null) {
		throw new Error('ProcessInstanceProvider is required');
	}
	return context;
}

export {ProcessInstanceContext, useProcessInstancePage};
