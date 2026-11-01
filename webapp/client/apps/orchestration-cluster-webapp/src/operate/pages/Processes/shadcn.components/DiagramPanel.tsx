/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useContext} from 'react';
import {useTranslation} from 'react-i18next';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {Diagram} from '#/operate/shared/Diagram/shadcn.components/Diagram';
import {DiagramOverlayContext} from '#/operate/shared/Diagram/DiagramOverlayContext';
import {DiagramShell} from '#/operate/shared/DiagramShell/shadcn.components/DiagramShell';
import {StateOverlay} from '#/operate/shared/StateOverlay/shadcn.components/StateOverlay';
import {useDiagramXml} from '../useDiagramXml';
import {useDiagramStatisticsOverlays} from '../useDiagramStatisticsOverlays';
import {getStatisticsFilter} from '../getStatisticsFilter';
import {getProcessDefinitionName} from '../getProcessDefinitionName';
import type {ProcessesSearch} from '../processesFilter';
import {DiagramHeader} from './DiagramHeader';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';

type StatisticsPayload = {elementState: React.ComponentProps<typeof StateOverlay>['state']; count?: number};

function isStatisticsPayload(payload: unknown): payload is StatisticsPayload {
	return (
		typeof payload === 'object' &&
		payload !== null &&
		'elementState' in payload &&
		(!('count' in payload) || typeof payload.count === 'number')
	);
}

const StatisticsOverlays: React.FC<{businessObjects?: BusinessObjects}> = ({businessObjects}) => {
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
};

const DiagramPanel: React.FC<Props> = ({
	processDefinitionSelection,
	isDefinitionSelectionLoading = false,
	isDefinitionSelectionError = false,
	search,
	onElementSelection,
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

	const statisticsFilter = getStatisticsFilter(search);
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
						selectedElementIds={search.elementId ? [search.elementId] : undefined}
						onElementSelection={onElementSelection}
						overlaysData={overlaysData}
						selectableElements={diagramData.selectableElements}
					>
						<StatisticsOverlays businessObjects={diagramData.businessObjects} />
					</Diagram>
				)}
			</DiagramShell>
		</section>
	);
};

export {DiagramPanel};
