/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext} from 'react';
import {useTranslation} from 'react-i18next';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {Diagram} from '#/operate/shared/Diagram';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {DiagramOverlayContext} from '#/operate/shared/Diagram/DiagramOverlayContext';
import {StateOverlay, type ElementState} from '#/operate/shared/StateOverlay/StateOverlay';
import {DiagramHeader} from './DiagramHeader';
import {useDiagramXml} from './useDiagramXml';
import {useDiagramStatisticsOverlays} from './useDiagramStatisticsOverlays';
import {getStatisticsFilter} from './getStatisticsFilter';
import {getProcessDefinitionName} from './getProcessDefinitionName';
import {MODIFICATIONS_BADGE} from '#/operate/shared/utils/badgePositions';
import {
	Section,
	BatchModificationNotificationContainer,
	BatchModificationInlineNotification,
	UndoButton,
} from './styled';
import {isMoveTarget} from './batchModificationTargets';
import type {ProcessesMode} from './ProcessesLayout';
import {useBatchModificationStatistics, type BatchModificationScope} from './useBatchModificationStatistics';
import {getElementName} from './getElementName';
import {ModificationBadgeOverlay} from './ModificationBadgeOverlay';
import type {VariableCondition} from './VariablesFilter/variableConditions';
import type {ProcessesSearch} from './processesFilter';

type ProcessDefinitionSelection =
	| {kind: 'no-match'}
	| {kind: 'single-version'; definition: ProcessDefinition}
	| {kind: 'all-versions'; definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>}
	| {kind: 'multiple-tenants'; definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>};

function isStatisticsPayload(
	payload: unknown,
): payload is {elementState: ElementState | 'completedEndEvents'; count?: number} {
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

function StatisticsOverlays() {
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
			/>
		);
	});
}

type Props = {
	processDefinitionSelection: ProcessDefinitionSelection;
	isDefinitionSelectionLoading?: boolean;
	isDefinitionSelectionError?: boolean;
	elementId?: string;
	onElementSelection: (elementId?: string) => void;
	mode?: ProcessesMode;
	modificationScope?: BatchModificationScope;
	selectedTargetElementId?: string;
	onTargetElementSelection?: (elementId?: string) => void;
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
	suspended: boolean;
	variable?: VariableCondition[];
	otherFilters?: Pick<
		ProcessesSearch,
		| 'tenantId'
		| 'businessId'
		| 'processInstanceKey'
		| 'parentProcessInstanceKey'
		| 'batchOperationKey'
		| 'errorMessage'
		| 'incidentErrorHashCode'
		| 'hasRetriesLeft'
		| 'startDateFrom'
		| 'startDateTo'
		| 'endDateFrom'
		| 'endDateTo'
	>;
};

const DiagramPanel: React.FC<Props> = ({
	processDefinitionSelection,
	isDefinitionSelectionLoading = false,
	isDefinitionSelectionError = false,
	elementId,
	onElementSelection,
	mode = 'list',
	modificationScope,
	selectedTargetElementId,
	onTargetElementSelection,
	active,
	incidents,
	completed,
	canceled,
	suspended,
	variable,
	otherFilters,
}) => {
	const {t} = useTranslation();
	const selectedDefinitionKey =
		processDefinitionSelection.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const selectedDefinitionName =
		processDefinitionSelection.kind !== 'no-match'
			? getProcessDefinitionName(processDefinitionSelection.definition)
			: t('operate.processes.diagramHeader.title');

	const {data: diagramData, isFetching: isXmlFetching, isError: isXmlError} = useDiagramXml(selectedDefinitionKey);
	const isModificationMode = mode === 'batch-modification';
	const modificationCount = useBatchModificationStatistics({
		definitionKey: isModificationMode ? selectedDefinitionKey : undefined,
		sourceElementId: isModificationMode ? elementId : undefined,
		scope: isModificationMode ? modificationScope : undefined,
	});
	const modificationOverlays =
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

	const statisticsFilter = getStatisticsFilter({
		active,
		incidents,
		completed,
		canceled,
		suspended,
		elementId,
		variable,
		...otherFilters,
	});
	const {data: overlaysData} = useDiagramStatisticsOverlays({
		processDefinitionKey: selectedDefinitionKey,
		filter: statisticsFilter ?? {},
		businessObjects: diagramData?.businessObjects,
		enabled: statisticsFilter !== undefined,
	});

	const getStatus = () => {
		if (isDefinitionSelectionLoading || isXmlFetching) {
			return 'loading';
		}
		if (isDefinitionSelectionError || isXmlError) {
			return 'error';
		}
		if (processDefinitionSelection.kind !== 'single-version' || diagramData?.xml === '') {
			return 'empty';
		}
		return 'content';
	};

	return (
		<Section aria-label="Diagram Panel">
			<DiagramHeader processDefinitionSelection={processDefinitionSelection} />
			<DiagramShell
				status={getStatus()}
				emptyMessage={
					processDefinitionSelection.kind === 'all-versions'
						? {
								message: t('operate.processes.diagramPanel.multipleVersionsSelected', {name: selectedDefinitionName}),
								additionalInfo: t('operate.processes.diagramPanel.selectSingleVersion'),
							}
						: processDefinitionSelection.kind === 'multiple-tenants'
							? {
									message: t('operate.processes.diagramPanel.multipleTenantsSelected', {name: selectedDefinitionName}),
									additionalInfo: t('operate.processes.diagramPanel.selectSingleTenant'),
								}
							: processDefinitionSelection.kind === 'single-version'
								? {message: t('operate.processInstance.diagram.noDiagram')}
								: {
										message: t('operate.processes.diagramPanel.noProcessSelected'),
										additionalInfo: t('operate.processes.diagramPanel.selectProcessInFilters'),
									}
				}
			>
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
						overlaysData={
							isModificationMode
								? [
										...(overlaysData?.filter(
											({payload}) => isStatisticsPayload(payload) && payload.count !== undefined,
										) ?? []),
										...modificationOverlays,
									]
								: overlaysData
						}
						selectableElements={
							isModificationMode
								? diagramData.selectableElements.filter(
										(id) => id !== elementId && isMoveTarget(diagramData.businessObjects[id]),
									)
								: diagramData.selectableElements
						}
					>
						<StatisticsOverlays />
					</Diagram>
				)}
			</DiagramShell>
			{isModificationMode && (
				<BatchModificationNotificationContainer>
					<BatchModificationInlineNotification
						hideCloseButton
						lowContrast
						kind="info"
						title=""
						subtitle={
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
						<UndoButton kind="ghost" size="sm" onClick={() => onTargetElementSelection?.(undefined)}>
							{t('operate.processes.batchModification.undo')}
						</UndoButton>
					)}
				</BatchModificationNotificationContainer>
			)}
		</Section>
	);
};

export {DiagramPanel};
export type {ProcessDefinitionSelection};
