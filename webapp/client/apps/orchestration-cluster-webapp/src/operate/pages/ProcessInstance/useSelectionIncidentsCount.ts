/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {selectionIncidentsCountQuery} from './processInstance.queries';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';

function useSelectionIncidentsCount(processInstance: ProcessInstance) {
	const {resolvedElementInstance, isFetchingElement, selectedElementId} = useProcessInstanceElementSelection();
	const elementInstanceKey = resolvedElementInstance?.elementInstanceKey;
	const isElementInstanceSelected =
		elementInstanceKey !== undefined && elementInstanceKey !== processInstance.processInstanceKey;
	const {data} = useQuery({
		...selectionIncidentsCountQuery({
			processInstanceKey: processInstance.processInstanceKey,
			...(isElementInstanceSelected ? {elementInstanceKey} : {elementId: selectedElementId ?? undefined}),
		}),
		enabled: processInstance.hasIncident && (isElementInstanceSelected || !isFetchingElement),
	});

	return processInstance.hasIncident ? data : undefined;
}

export {useSelectionIncidentsCount};
