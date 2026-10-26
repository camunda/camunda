/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Popover, PopoverContent, Stack} from '@carbon/react';
import {Add, ArrowRight, Error} from '@carbon/react/icons';
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
import {
	DropdownAnchor,
	DropdownButton,
	DropdownContent,
	DropdownInfoMessage,
	DropdownInstanceCount,
	DropdownLoading,
	DropdownTitle,
	DropdownUnsupported,
} from './processInstanceModification.styled';

type Props = {
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

function ModificationDropdown({
	businessObjects,
	statistics,
	runningInstancesByElement,
	modificationsByElement,
	selectedElementRef,
}: Props) {
	const {t} = useTranslation();
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
		return null;
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
	const {top, left, width, height} = anchorRect;

	const content = (() => {
		if (isFetchingElement) {
			return <DropdownLoading data-testid="dropdown-spinner" />;
		}
		if (!canBeModified) {
			return <DropdownUnsupported>{t('operate.processInstance.modification.unsupportedElement')}</DropdownUnsupported>;
		}
		if (availableModifications.length === 0) {
			return <DropdownUnsupported>{t('operate.processInstance.modification.noModifications')}</DropdownUnsupported>;
		}

		return (
			<>
				{runningInstancesCount > 0 && (
					<DropdownInstanceCount>
						{t('operate.processInstance.modification.selectedRunningInstances', {count: runningInstancesCount})}
					</DropdownInstanceCount>
				)}
				{runningInstancesCount > 1 && (
					<DropdownInfoMessage>{t('operate.processInstance.modification.specificInstanceHint')}</DropdownInfoMessage>
				)}
				<Stack gap={2}>
					{availableModifications.includes('add') && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.addLabel')}
							aria-label={t('operate.processInstance.modification.addLabel')}
							size="sm"
							renderIcon={Add}
							onClick={() => {
								if (
									getTargetScopes(businessObjects[selectedElementId], runningInstancesByElement)
										.requiresParentScopeSelection
								) {
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
							}}
						>
							{t('operate.processInstance.modification.add')}
						</DropdownButton>
					)}
					{availableModifications.includes('cancel-instance') && resolvedElementInstanceKey !== undefined && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.cancelInstanceLabel')}
							aria-label={t('operate.processInstance.modification.cancelInstanceLabel')}
							size="sm"
							renderIcon={Error}
							onClick={() => {
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
							}}
						>
							{t('operate.processInstance.modification.cancelInstance')}
						</DropdownButton>
					)}
					{availableModifications.includes('cancel-all') && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.cancelAllLabel')}
							aria-label={t('operate.processInstance.modification.cancelAllLabel')}
							size="sm"
							renderIcon={Error}
							onClick={() => {
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
							}}
						>
							{t('operate.processInstance.modification.cancelAll')}
						</DropdownButton>
					)}
					{availableModifications.includes('move-instance') && resolvedElementInstanceKey !== undefined && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.moveInstanceLabel')}
							aria-label={t('operate.processInstance.modification.moveInstanceLabel')}
							size="sm"
							renderIcon={ArrowRight}
							onClick={() => {
								dispatch({
									type: 'startMovingToken',
									elementId: selectedElementId,
									elementInstanceKey: resolvedElementInstanceKey,
								});
								clearSelection();
							}}
						>
							{t('operate.processInstance.modification.moveInstance')}
						</DropdownButton>
					)}
					{availableModifications.includes('move-all') && (
						<DropdownButton
							kind="ghost"
							title={t('operate.processInstance.modification.moveAllLabel')}
							size="sm"
							renderIcon={ArrowRight}
							onClick={() => {
								dispatch({type: 'startMovingToken', elementId: selectedElementId});
								clearSelection();
							}}
						>
							{t('operate.processInstance.modification.moveAll')}
						</DropdownButton>
					)}
				</Stack>
			</>
		);
	})();

	return (
		<Popover open align="bottom" caret autoAlign>
			<DropdownAnchor style={{top, left, width, height}} />
			<PopoverContent>
				<DropdownContent data-testid="popover">
					<Stack gap={3}>
						<DropdownTitle>{t('operate.processInstance.modification.elementModifications')}</DropdownTitle>
						<Stack gap={4}>{content}</Stack>
					</Stack>
				</DropdownContent>
			</PopoverContent>
		</Popover>
	);
}

export {ModificationDropdown};
