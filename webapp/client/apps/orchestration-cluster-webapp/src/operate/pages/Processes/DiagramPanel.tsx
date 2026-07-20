/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext} from 'react';
import {useTranslation} from 'react-i18next';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {Diagram} from '#/operate/shared/Diagram';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {DiagramOverlayContext} from '#/operate/shared/Diagram/DiagramOverlayContext';
import {StateOverlay, type ElementState} from '#/operate/shared/StateOverlay/StateOverlay';
import {DiagramHeader} from './DiagramHeader';
import {useDiagramXml} from './useDiagramXml';
import {useDiagramStatisticsOverlays} from './useDiagramStatisticsOverlays';
import {getStatisticsFilter} from './getStatisticsFilter';
import {Section} from './styled';

type ProcessDefinitionSelection =
	| {kind: 'no-match'}
	| {kind: 'single-version'; definition: ProcessDefinition}
	| {kind: 'all-versions'; definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>};

function isStatisticsPayload(
	payload: unknown,
): payload is {elementState: ElementState | 'completedEndEvents'; count: number} {
	return typeof payload === 'object' && payload !== null && 'elementState' in payload && 'count' in payload;
}

function StatisticsOverlays() {
	const overlays = useContext(DiagramOverlayContext);

	return overlays.map(({container, payload, elementId}) => {
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

function getProcessDefinitionName(definition: Pick<ProcessDefinition, 'name' | 'processDefinitionId'>) {
	return definition.name ?? definition.processDefinitionId;
}

type Props = {
	processDefinitionSelection: ProcessDefinitionSelection;
	elementId?: string;
	onElementSelection: (elementId?: string) => void;
	active: boolean;
	incidents: boolean;
	completed: boolean;
	canceled: boolean;
};

const DiagramPanel: React.FC<Props> = ({
	processDefinitionSelection,
	elementId,
	onElementSelection,
	active,
	incidents,
	completed,
	canceled,
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

	const statisticsFilter = getStatisticsFilter({active, incidents, completed, canceled});
	const {data: overlaysData} = useDiagramStatisticsOverlays({
		processDefinitionKey: selectedDefinitionKey,
		filter: statisticsFilter ?? {},
		businessObjects: diagramData?.businessObjects,
		enabled: statisticsFilter !== undefined,
	});

	const getStatus = () => {
		if (isXmlFetching) {
			return 'loading';
		}
		if (isXmlError) {
			return 'error';
		}
		if (processDefinitionSelection.kind !== 'single-version') {
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
						: {
								message: t('operate.processes.diagramPanel.noProcessSelected'),
								additionalInfo: t('operate.processes.diagramPanel.selectProcessInFilters'),
							}
				}
			>
				{diagramData?.xml !== undefined && (
					<Diagram
						key={selectedDefinitionKey}
						xml={diagramData.xml}
						selectedElementIds={elementId ? [elementId] : undefined}
						onElementSelection={onElementSelection}
						overlaysData={overlaysData}
						selectableElements={diagramData.selectableElements}
					>
						<StatisticsOverlays />
					</Diagram>
				)}
			</DiagramShell>
		</Section>
	);
};

export {DiagramPanel};
export type {ProcessDefinitionSelection};
