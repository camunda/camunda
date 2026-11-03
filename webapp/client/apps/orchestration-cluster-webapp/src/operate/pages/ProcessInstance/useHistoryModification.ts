/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from '#/operate/shared/utils/elements';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useInstanceStatistics} from './instanceDiagram.queries';
import {createFinishAddingTokenAction} from './modifications/modificationOperations';
import {canBeAddedTokenAncestor, getRunningInstancesByElement} from './modifications/modificationTargets';
import {getModificationsByElement, hasPendingCancelOrMoveModification} from './modifications/modificationSelectors';
import {getModificationPlaceholders, type HistoryPlaceholder} from './modifications/historyPlaceholders';

function getPlaceholderTreeKey(placeholderKey: string, parentTreeKey: string) {
	return `${placeholderKey}@${parentTreeKey}`;
}

function useHistoryModification({
	businessObjects,
	isXmlLoaded,
}: {
	businessObjects?: BusinessObjects;
	isXmlLoaded: boolean;
}) {
	const {processInstance, processInstanceId} = useProcessInstancePage();
	const selection = useProcessInstanceElementSelection();
	const {isModificationModeEnabled, state: modificationState, dispatch} = useProcessInstanceModification();
	const statistics = useInstanceStatistics(processInstance, isModificationModeEnabled && isXmlLoaded);
	const {placeholders, modificationsByElement} = useMemo(() => {
		const runningInstancesByElement = getRunningInstancesByElement(statistics.data, businessObjects);
		return {
			placeholders:
				isModificationModeEnabled && businessObjects !== undefined
					? getModificationPlaceholders(
							modificationState,
							businessObjects,
							processInstance.processDefinitionId,
							processInstanceId,
						)
					: [],
			modificationsByElement: getModificationsByElement(modificationState, {
				businessObjects,
				totalRunningInstancesByElement: runningInstancesByElement,
				totalRunningInstancesVisibleByElement: runningInstancesByElement,
			}),
		};
	}, [
		statistics.data,
		businessObjects,
		isModificationModeEnabled,
		modificationState,
		processInstance.processDefinitionId,
		processInstanceId,
	]);
	const placeholderSession = `${processInstanceId}/${isModificationModeEnabled}`;
	const [expanded, setExpanded] = useState({session: placeholderSession, keys: [] as string[]});
	const expandedPlaceholders = expanded.session === placeholderSession ? expanded.keys : [];
	const togglePlaceholder = (key: string) =>
		setExpanded({
			session: placeholderSession,
			keys: expandedPlaceholders.includes(key)
				? expandedPlaceholders.filter((expandedKey) => expandedKey !== key)
				: [...expandedPlaceholders, key],
		});

	const hasPlannedCancel = (item: Pick<ElementInstance, 'elementId'>, hierarchy: string[]) =>
		modificationsByElement[item.elementId]?.areAllTokensCanceled === true ||
		hierarchy.some((elementInstanceKey) =>
			hasPendingCancelOrMoveModification(modificationState, {
				elementId: item.elementId,
				elementInstanceKey,
				modificationsByElement,
			}),
		);

	const handleModificationSelect = (item: ElementInstance, hasPlannedCancelOfItem: boolean) => {
		if (modificationState.status === 'moving-token') {
			return true;
		}
		if (modificationState.status === 'adding-token') {
			const sourceElementId = modificationState.sourceElementIdForAddOperation;
			if (
				businessObjects !== undefined &&
				sourceElementId !== null &&
				!hasPlannedCancelOfItem &&
				canBeAddedTokenAncestor(businessObjects, sourceElementId, item)
			) {
				dispatch(
					createFinishAddingTokenAction(modificationState, {
						businessObjects,
						ancestorElementId: item.elementId,
						ancestorElementInstanceKey: item.elementInstanceKey,
					}),
				);
			}
			return true;
		}
		return false;
	};

	const selectPlaceholder = (elementId: string, elementInstanceKey: string) => {
		if (modificationState.status === 'moving-token' || modificationState.status === 'adding-token') {
			return;
		}
		selection.selectPlaceholder(elementId, elementInstanceKey, isMultiInstance(businessObjects?.[elementId]));
	};

	return {
		isModificationModeEnabled,
		placeholders,
		modificationsByElement,
		expandedPlaceholders,
		togglePlaceholder,
		hasPlannedCancel,
		handleModificationSelect,
		selectPlaceholder,
	};
}

export {getPlaceholderTreeKey, useHistoryModification};
export type {HistoryPlaceholder};
