/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext, useMemo} from 'react';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {ProcessDefinitionStatistic} from '@camunda/camunda-api-zod-schemas/8.11';
import {MODIFICATIONS_BADGE} from '#/operate/shared/utils/badgePositions';
import {useProcessInstancePage} from './useProcessInstancePage';
import {type ModificationBadge} from './useInstanceDiagramOverlays';
import {InstanceHistoryContext} from './useInstanceHistory';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {createFinishMovingTokenAction} from './modifications/modificationOperations';
import {getModificationsByElement} from './modifications/modificationSelectors';
import {getLoadedInstanceAncestors} from './modifications/historyPlaceholders';
import {
	getAncestorScopeType,
	getModifiableElements,
	getRunningInstancesByElement,
} from './modifications/modificationTargets';

function useInstanceDiagramModification({
	businessObjects,
	statistics,
	selectElement,
	clearSelection,
}: {
	businessObjects?: BusinessObjects;
	statistics?: ProcessDefinitionStatistic[];
	selectElement: (elementId: string, isMultiInstanceBody?: boolean) => void;
	clearSelection: () => void;
}) {
	const {processInstanceId, processInstance} = useProcessInstancePage();
	const {state: modificationState, dispatch: dispatchModification} = useProcessInstanceModification();
	const history = useContext(InstanceHistoryContext);
	const runningInstancesByElement = useMemo(
		() => getRunningInstancesByElement(statistics, businessObjects),
		[statistics, businessObjects],
	);
	const modificationsByElement = useMemo(
		() =>
			getModificationsByElement(modificationState, {
				businessObjects,
				totalRunningInstancesByElement: runningInstancesByElement,
				totalRunningInstancesVisibleByElement: runningInstancesByElement,
			}),
		[modificationState, businessObjects, runningInstancesByElement],
	);
	const modificationBadges = useMemo<ModificationBadge[]>(
		() =>
			Object.entries(modificationsByElement).map(([elementId, {newTokens, visibleCancelledTokens}]) => ({
				elementId,
				type: 'instance-modification',
				position: MODIFICATIONS_BADGE,
				payload: {newTokenCount: newTokens, cancelledTokenCount: visibleCancelledTokens},
			})),
		[modificationsByElement],
	);
	const modifiableElements = useMemo(
		() => getModifiableElements(modificationState, businessObjects, statistics, runningInstancesByElement),
		[modificationState, businessObjects, statistics, runningInstancesByElement],
	);

	const finishMovingToken = (targetElementId?: string) => {
		if (businessObjects === undefined) {
			return;
		}
		const sourceElementId = modificationState.sourceElementIdForMoveOperation;
		const sourceElementInstanceKey = modificationState.sourceElementInstanceKeyForMoveOperation;
		const sourceAncestors =
			sourceElementInstanceKey === null
				? []
				: getLoadedInstanceAncestors(
						[...(history?.windows.values() ?? [])].map(({scope, query}) => ({
							key: scope.key,
							items: query.data?.items ?? [],
						})),
						sourceElementInstanceKey,
						processInstanceId,
						processInstance.processDefinitionId,
					);
		const affectedTokenCount = modificationState.sourceElementInstanceKeyForMoveOperation
			? 1
			: (sourceElementId ? runningInstancesByElement[sourceElementId] : undefined) || 1;
		dispatchModification(
			createFinishMovingTokenAction(modificationState, {
				businessObjects,
				affectedTokenCount,
				visibleAffectedTokenCount: affectedTokenCount,
				bpmnProcessId: processInstance.processDefinitionId,
				runningInstancesByElement,
				sourceAncestors,
				targetElementId,
				ancestorScopeType:
					targetElementId === undefined
						? undefined
						: getAncestorScopeType(businessObjects, sourceElementId ?? '', targetElementId, runningInstancesByElement),
			}),
		);
	};

	const handleModificationElementSelection = (elementId?: string, isMultiInstance?: boolean) => {
		if (modificationState.status === 'moving-token' && businessObjects !== undefined) {
			clearSelection();
			finishMovingToken(elementId);
			return;
		}
		if (modificationState.status === 'adding-token') {
			return;
		}
		if (elementId) {
			selectElement(elementId, isMultiInstance);
		} else {
			clearSelection();
		}
	};

	return {
		runningInstancesByElement,
		modificationsByElement,
		modificationBadges,
		modifiableElements,
		finishMovingToken,
		handleModificationElementSelection,
	};
}

export {useInstanceDiagramModification};
