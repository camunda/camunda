/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';

function useCanEditVariables() {
	const {processInstance} = useProcessInstancePage();
	const {hasSelection, resolvedElementInstance} = useProcessInstanceElementSelection();

	if (hasSelection && resolvedElementInstance?.state !== 'ACTIVE') {
		return false;
	}

	if (processInstance.state === 'SUSPENDED') {
		return resolvedElementInstance?.type !== 'USER_TASK';
	}

	return processInstance.state === 'ACTIVE';
}

export {useCanEditVariables};
