/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useReducer, useState} from 'react';
import {
	initialModificationState,
	modificationReducer,
	type ModificationAction,
	type ModificationState,
} from './modifications/modificationReducer';
import {isModificationModeEnabled} from './modifications/modificationSelectors';

type ModificationController = {
	state: ModificationState;
	isModificationModeEnabled: boolean;
	dispatch: (action: ModificationAction) => void;
};

type ProcessInstanceModification = ModificationController & {
	enterModificationMode: () => void;
	discardModifications: () => void;
};

function useModificationController(processInstanceId: string): ModificationController {
	const [state, dispatch] = useReducer(modificationReducer, initialModificationState);
	const [ownerId, setOwnerId] = useState(processInstanceId);
	if (ownerId !== processInstanceId) {
		setOwnerId(processInstanceId);
		dispatch({type: 'reset'});
	}

	return {state, isModificationModeEnabled: isModificationModeEnabled(state), dispatch};
}

const ProcessInstanceModificationContext = createContext<ProcessInstanceModification>({
	state: initialModificationState,
	isModificationModeEnabled: false,
	dispatch: () => {},
	enterModificationMode: () => {},
	discardModifications: () => {},
});

function useProcessInstanceModification() {
	return useContext(ProcessInstanceModificationContext);
}

export {ProcessInstanceModificationContext, useModificationController, useProcessInstanceModification};
export type {ModificationController};
