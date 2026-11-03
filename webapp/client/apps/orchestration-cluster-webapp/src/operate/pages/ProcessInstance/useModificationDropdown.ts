/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useState} from 'react';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {ProcessDefinitionStatistic} from '@camunda/camunda-api-zod-schemas/8.11';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {createAddTokenModification, createCancelTokenModification} from './modifications/modificationOperations';
import type {getModificationsByElement} from './modifications/modificationSelectors';
import {
	canElementBeModified,
	getAvailableModifications,
	getTargetScopes,
	type RunningInstancesByElement,
} from './modifications/modificationTargets';

type ModificationDropdownParams = {
	businessObjects: BusinessObjects;
	statistics?: ProcessDefinitionStatistic[];
	runningInstancesByElement: RunningInstancesByElement;
	modificationsByElement: ReturnType<typeof getModificationsByElement>;
	selectedElementRef?: SVGElement;
};

function useElementRect(element?: Element) {
	const [rect, setRect] = useState(() => element?.getBoundingClientRect());

	useLayoutEffect(() => {
		if (element === undefined) {
			return;
		}
		const update = () => setRect(element.getBoundingClientRect());
		const observer = new ResizeObserver(update);
		observer.observe(element.closest('.djs-container') ?? element);
		window.addEventListener('resize', update);
		window.addEventListener('scroll', update, true);
		return () => {
			observer.disconnect();
			window.removeEventListener('resize', update);
			window.removeEventListener('scroll', update, true);
		};
	}, [element]);

	return rect;
}

function useModificationDropdown({
	businessObjects,
	statistics,
	runningInstancesByElement,
	modificationsByElement,
	selectedElementRef,
}: ModificationDropdownParams) {
	const {processInstance} = useProcessInstancePage();
	const {state, dispatch} = useProcessInstanceModification();
	const {
		selectedElementId,
		selectedElementInstanceKey,
		isFetchingElement,
		isSelectedInstanceMultiInstanceBody,
		resolvedElementInstance,
		clearSelection,
	} = useProcessInstanceElementSelection();
	const anchorRect = useElementRect(selectedElementRef);

	if (
		selectedElementId === null ||
		state.status === 'moving-token' ||
		selectedElementRef === undefined ||
		anchorRect === undefined
	) {
		return undefined;
	}

	const isSpecificElementInstanceSelected = selectedElementInstanceKey !== null;
	const isResolvedInstanceRunning =
		resolvedElementInstance?.state === 'ACTIVE' || resolvedElementInstance?.hasIncident === true;
	const totalRunningInstancesCount = runningInstancesByElement[selectedElementId] ?? 0;
	const runningInstancesCount =
		isSpecificElementInstanceSelected && !isResolvedInstanceRunning
			? 0
			: isResolvedInstanceRunning && !isSelectedInstanceMultiInstanceBody
				? 1
				: totalRunningInstancesCount;
	const resolvedElementInstanceKey = resolvedElementInstance?.elementInstanceKey;
	const canBeModified = canElementBeModified(
		state,
		businessObjects,
		statistics,
		runningInstancesByElement,
		selectedElementId,
	);
	const availableModifications = getAvailableModifications({
		state,
		businessObjects,
		statistics,
		runningInstancesByElement,
		modificationsByElement,
		elementId: selectedElementId,
		selectedElementInstanceKey,
		runningElementInstanceCount: runningInstancesCount,
		isMultiInstanceBody: isSelectedInstanceMultiInstanceBody,
		isSingleRunningInstanceResolved: isResolvedInstanceRunning,
	});

	const addToken = () => {
		if (getTargetScopes(businessObjects[selectedElementId], runningInstancesByElement).requiresParentScopeSelection) {
			dispatch({type: 'startAddingToken', elementId: selectedElementId});
		} else {
			dispatch({
				type: 'addModification',
				modification: {
					type: 'token',
					payload: createAddTokenModification(state, {
						businessObjects,
						elementId: selectedElementId,
						bpmnProcessId: processInstance.processDefinitionId,
						runningInstancesByElement,
					}),
				},
			});
		}
		clearSelection();
	};

	const cancelInstance = () => {
		dispatch({
			type: 'addModification',
			modification: {
				type: 'token',
				payload: createCancelTokenModification({
					businessObjects,
					elementId: selectedElementId,
					elementInstanceKey: resolvedElementInstanceKey,
					affectedTokenCount: 1,
					visibleAffectedTokenCount: 1,
				}),
			},
		});
		clearSelection();
	};

	const cancelAll = () => {
		dispatch({
			type: 'addModification',
			modification: {
				type: 'token',
				payload: createCancelTokenModification({
					businessObjects,
					elementId: selectedElementId,
					affectedTokenCount: totalRunningInstancesCount,
					visibleAffectedTokenCount: totalRunningInstancesCount,
				}),
			},
		});
		clearSelection();
	};

	const moveInstance = () => {
		dispatch({type: 'startMovingToken', elementId: selectedElementId, elementInstanceKey: resolvedElementInstanceKey});
		clearSelection();
	};

	const moveAll = () => {
		dispatch({type: 'startMovingToken', elementId: selectedElementId});
		clearSelection();
	};

	return {
		anchorRect,
		isFetchingElement,
		canBeModified,
		availableModifications,
		runningInstancesCount,
		hasResolvedInstance: resolvedElementInstanceKey !== undefined,
		addToken,
		cancelInstance,
		cancelAll,
		moveInstance,
		moveAll,
	};
}

export {useModificationDropdown};
export type {ModificationDropdownParams};
