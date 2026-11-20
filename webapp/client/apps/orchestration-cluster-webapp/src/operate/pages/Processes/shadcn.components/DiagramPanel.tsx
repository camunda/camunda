/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {Diagram} from '#/operate/shared/Diagram/shadcn.components/Diagram';
import {Alert, Button} from '@camunda/design-system';
import {DiagramOverlayContext} from '#/operate/shared/Diagram/DiagramOverlayContext';
import {DiagramShell} from '#/operate/shared/DiagramShell/shadcn.components/DiagramShell';
import {ModificationBadgeOverlay} from '#/operate/shared/ModificationBadgeOverlay/shadcn.components/ModificationBadgeOverlay';
import {MODIFICATIONS_BADGE} from '#/operate/shared/utils/badgePositions';
import {StateOverlay} from '#/operate/shared/StateOverlay/shadcn.components/StateOverlay';
import {anchorStateOverlaysToCorners} from '#/operate/shared/StateOverlay/shadcn.components/anchorStateOverlaysToCorners';
import {useDiagramXml} from '../useDiagramXml';
import {useDiagramStatisticsOverlays} from '../useDiagramStatisticsOverlays';
import {getStatisticsFilter} from '../getStatisticsFilter';
import {getProcessDefinitionName} from '../getProcessDefinitionName';
import {getElementName} from '../getElementName';
import {isMoveTarget} from '../batchModificationTargets';
import {useBatchModificationStatistics, type BatchModificationScope} from '../useBatchModificationStatistics';
import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';
import type {ProcessesSearch} from '../processesFilter';
import {DiagramHeader} from './DiagramHeader';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';
import type {ProcessesMode} from './processesMode';

type StatisticsPayload = {elementState: React.ComponentProps<typeof StateOverlay>['state']; count?: number};

function isStatisticsPayload(payload: unknown): payload is StatisticsPayload {
	return (
		typeof payload === 'object' &&
		payload !== null &&
		'elementState' in payload &&
		(!('count' in payload) || typeof payload.count === 'number')
	);
}

function isModificationBadgePayload(
	payload: unknown,
): payload is {newTokenCount?: number; cancelledTokenCount?: number} {
	return (
		typeof payload === 'object' && payload !== null && ('newTokenCount' in payload || 'cancelledTokenCount' in payload)
	);
}

const StatisticsOverlays: React.FC<{businessObjects?: BusinessObjects}> = ({businessObjects}) => {
	const overlays = useContext(DiagramOverlayContext);

	return overlays.map(({container, payload, elementId, type}) => {
		if (type === 'batchModificationsBadge' && isModificationBadgePayload(payload)) {
			return (
				<ModificationBadgeOverlay
					key={`${elementId}-${type}`}
					container={container}
					newTokenCount={payload.newTokenCount ?? 0}
					cancelledTokenCount={payload.cancelledTokenCount ?? 0}
				/>
			);
		}
		if (!isStatisticsPayload(payload)) {
			return null;
		}
		return (
			<StateOverlay
				key={`${elementId}-${payload.elementState}`}
				testId={`state-overlay-${elementId}-${payload.elementState}`}
				state={payload.elementState}
				count={payload.count}
				container={container}
				elementName={businessObjects?.[elementId]?.name}
			/>
		);
	});
};

type Props = {
	processDefinitionSelection: ProcessDefinitionSelection;
	isDefinitionSelectionLoading?: boolean;
	isDefinitionSelectionError?: boolean;
	search: ProcessesSearch;
	onElementSelection: (elementId?: string) => void;
	mode?: ProcessesMode;
	modificationScope?: BatchModificationScope;
	selectedTargetElementId?: string;
	onTargetElementSelection?: (elementId?: string) => void;
};

const DiagramPanel: React.FC<Props> = ({
	processDefinitionSelection,
	isDefinitionSelectionLoading = false,
	isDefinitionSelectionError = false,
	search,
	onElementSelection,
	mode = 'list',
	modificationScope,
	selectedTargetElementId,
	onTargetElementSelection,
}) => {
	const {t} = useTranslation();
	const selectedDefinitionKey =
		processDefinitionSelection.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const selectedDefinitionName =
		processDefinitionSelection.kind === 'no-match'
			? t('operate.processes.diagramHeader.title')
			: getProcessDefinitionName(processDefinitionSelection.definition);

	const {data: diagramData, isFetching: isXmlFetching, isError: isXmlError} = useDiagramXml(selectedDefinitionKey);
	const [failedXml, setFailedXml] = useState<string>();
	const isRenderError = failedXml !== undefined && failedXml === diagramData?.xml;

	const isModificationMode = mode === 'batch-modification';
	const {elementId} = search;
	const modificationCount = useBatchModificationStatistics({
		definitionKey: isModificationMode ? selectedDefinitionKey : undefined,
		sourceElementId: isModificationMode ? elementId : undefined,
		scope: isModificationMode ? modificationScope : undefined,
	});
	const modificationOverlays: OverlayData[] =
		elementId !== undefined && selectedTargetElementId !== undefined && modificationCount !== undefined
			? [
					{
						payload: {cancelledTokenCount: modificationCount},
						type: 'batchModificationsBadge',
						elementId,
						position: MODIFICATIONS_BADGE,
					},
					{
						payload: {newTokenCount: modificationCount},
						type: 'batchModificationsBadge',
						elementId: selectedTargetElementId,
						position: MODIFICATIONS_BADGE,
					},
				]
			: [];
	const sourceElementName = getElementName({businessObjects: diagramData?.businessObjects, elementId});
	const targetElementName = getElementName({
		businessObjects: diagramData?.businessObjects,
		elementId: selectedTargetElementId,
	});

	const statisticsFilter = getStatisticsFilter(search);
	const {data: overlaysData} = useDiagramStatisticsOverlays({
		processDefinitionKey: selectedDefinitionKey,
		filter: statisticsFilter ?? {},
		businessObjects: diagramData?.businessObjects,
		enabled: statisticsFilter !== undefined,
	});

	const anchoredOverlaysData = useMemo(
		() => (overlaysData ? anchorStateOverlaysToCorners(overlaysData) : undefined),
		[overlaysData],
	);
	const displayedOverlaysData = isModificationMode
		? [
				...(anchoredOverlaysData?.filter(({payload}) => isStatisticsPayload(payload) && payload.count !== undefined) ??
					[]),
				...modificationOverlays,
			]
		: anchoredOverlaysData;

	const getStatus = () => {
		if (isDefinitionSelectionLoading || isXmlFetching) {
			return 'loading';
		}
		if (isDefinitionSelectionError || isXmlError || isRenderError) {
			return 'error';
		}
		if (processDefinitionSelection.kind !== 'single-version' || diagramData?.xml === '') {
			return 'empty';
		}
		return 'content';
	};

	const getEmptyMessage = () => {
		switch (processDefinitionSelection.kind) {
			case 'all-versions':
				return {
					message: t('operate.processes.diagramPanel.multipleVersionsSelected', {name: selectedDefinitionName}),
					additionalInfo: t('operate.processes.diagramPanel.selectSingleVersion'),
				};
			case 'multiple-tenants':
				return {
					message: t('operate.processes.diagramPanel.multipleTenantsSelected', {name: selectedDefinitionName}),
					additionalInfo: t('operate.processes.diagramPanel.selectSingleTenant'),
				};
			case 'single-version':
				return {message: t('operate.processInstance.diagram.noDiagram')};
			default:
				return {
					message: t('operate.processes.diagramPanel.noProcessSelected'),
					additionalInfo: t('operate.processes.diagramPanel.selectProcessInFilters'),
				};
		}
	};

	return (
		<section aria-label={t('operate.processes.diagramPanel.ariaLabel')} className="flex h-full flex-col">
			<DiagramHeader processDefinitionSelection={processDefinitionSelection} />
			<DiagramShell status={getStatus()} emptyMessage={getEmptyMessage()}>
				{diagramData?.xml && (
					<Diagram
						key={selectedDefinitionKey}
						xml={diagramData.xml}
						selectedElementIds={
							isModificationMode
								? [elementId, selectedTargetElementId].filter((id): id is string => id !== undefined)
								: elementId
									? [elementId]
									: undefined
						}
						onElementSelection={isModificationMode ? onTargetElementSelection : onElementSelection}
						onRenderError={() => setFailedXml(diagramData.xml)}
						overlaysData={displayedOverlaysData}
						selectableElements={
							isModificationMode
								? diagramData.selectableElements.filter(
										(id) => id !== elementId && isMoveTarget(diagramData.businessObjects[id]),
									)
								: diagramData.selectableElements
						}
					>
						<StatisticsOverlays businessObjects={diagramData.businessObjects} />
					</Diagram>
				)}
			</DiagramShell>
			{isModificationMode && (
				<div className="relative px-1 py-1">
					<Alert
						variant="info"
						className="py-1.5"
						role="status"
						description={
							sourceElementName === '' || targetElementName === ''
								? t('operate.processes.batchModification.selectTarget')
								: t('operate.processes.batchModification.targetScheduled', {
										count: modificationCount ?? 0,
										source: sourceElementName,
										target: targetElementName,
									})
						}
					/>
					{selectedTargetElementId && (
						<Button
							variant="secondary"
							size="sm"
							className="absolute right-3 top-1/2 -translate-y-1/2"
							onClick={() => onTargetElementSelection?.(undefined)}
						>
							{t('operate.processes.batchModification.undo')}
						</Button>
					)}
				</div>
			)}
		</section>
	);
};

export {DiagramPanel};
