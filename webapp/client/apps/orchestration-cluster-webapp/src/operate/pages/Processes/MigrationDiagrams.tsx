/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {ComboBox, Dropdown, Stack} from '@carbon/react';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {Diagram} from '#/operate/shared/Diagram';
import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';
import {DiagramShell} from '#/operate/shared/DiagramShell/DiagramShell';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/ResizablePanel';
import {StatisticsOverlays} from './DiagramPanel';
import {getProcessDefinitionName} from './getProcessDefinitionName';
import type {MigrationElements} from './migrationMapping';
import type {useDiagramXml} from './useDiagramXml';
import {useAvailableMigrationTargets, useMigrationTargetVersions} from './useMigrationTargetDefinitions';
import {
	MigrationDiagramHeader,
	MigrationDiagrams as Container,
	MigrationHeaderField,
	MigrationHeaderLabel,
	Section,
} from './styled';

type DiagramXmlQuery = ReturnType<typeof useDiagramXml>;

type Props = {
	source: ProcessDefinition;
	target: ProcessDefinition | null;
	sourceXml: DiagramXmlQuery;
	targetXml: DiagramXmlQuery;
	sourceElements: MigrationElements;
	targetElements?: MigrationElements;
	selectedSourceElementIds?: string[];
	selectedTargetElementId?: string;
	onSourceElementSelection: (elementId?: string) => void;
	onTargetElementSelection: (elementId?: string) => void;
	onTargetChange: (definition: ProcessDefinition | null) => void;
	isSummaryStep: boolean;
	sourceOverlays: OverlayData[];
	targetOverlays: OverlayData[];
};

function getSelectableElementIds({elements, sequenceFlows}: MigrationElements) {
	return [...elements, ...sequenceFlows].map(({id}) => id);
}

function getDiagramStatus({isLoading, isError, data}: DiagramXmlQuery) {
	if (isLoading) {
		return 'loading';
	}
	return isError || data?.xml === '' ? 'error' : 'content';
}

function MigrationDiagrams({
	source,
	target,
	sourceXml,
	targetXml,
	sourceElements,
	targetElements,
	selectedSourceElementIds,
	selectedTargetElementId,
	onSourceElementSelection,
	onTargetElementSelection,
	onTargetChange,
	isSummaryStep,
	sourceOverlays,
	targetOverlays,
}: Props) {
	const {t} = useTranslation();
	const containerRef = useRef<HTMLDivElement | null>(null);
	const [clientWidth, setClientWidth] = useState(0);
	const availableTargets = useAvailableMigrationTargets(source);
	const {data: targetVersions} = useMigrationTargetVersions(source, target);
	const processItems = useMemo(
		() =>
			availableTargets.map((definition) => ({
				id: definition.processDefinitionKey,
				label: getProcessDefinitionName(definition),
				definition,
			})),
		[availableTargets],
	);
	const versions = useMemo(() => targetVersions?.map(({version}) => version) ?? [], [targetVersions]);
	const panelMinWidth = clientWidth / 3;
	const targetStatus = getDiagramStatus(targetXml);

	useEffect(() => {
		const container = containerRef.current;
		if (container === null) {
			return;
		}

		const observer = new ResizeObserver(() => setClientWidth(container.clientWidth));
		observer.observe(container);

		return () => observer.disconnect();
	}, []);

	return (
		<Container ref={containerRef}>
			<ResizablePanel
				panelId="process-migration-diagrams-horizontal-panel"
				direction={SplitDirection.Horizontal}
				minWidths={[panelMinWidth, panelMinWidth]}
			>
				<Section>
					{!isSummaryStep && (
						<MigrationDiagramHeader orientation="horizontal" gap={6}>
							<Stack orientation="horizontal" gap={5}>
								<MigrationHeaderLabel>{t('operate.processes.migration.source')}</MigrationHeaderLabel>
								<span>{getProcessDefinitionName(source)}</span>
							</Stack>
							<Stack orientation="horizontal" gap={5}>
								<MigrationHeaderLabel>{t('operate.processes.migration.version')}</MigrationHeaderLabel>
								<span>{source.version}</span>
							</Stack>
						</MigrationDiagramHeader>
					)}
					<DiagramShell status={getDiagramStatus(sourceXml)}>
						{sourceXml.data?.xml && (
							<Diagram
								xml={sourceXml.data.xml}
								selectableElements={getSelectableElementIds(sourceElements)}
								selectedElementIds={selectedSourceElementIds}
								onElementSelection={onSourceElementSelection}
								overlaysData={isSummaryStep ? sourceOverlays : undefined}
							>
								<StatisticsOverlays />
							</Diagram>
						)}
					</DiagramShell>
				</Section>
				<Section>
					{!isSummaryStep && (
						<MigrationDiagramHeader orientation="horizontal" gap={6}>
							<Stack orientation="horizontal" gap={5}>
								<MigrationHeaderLabel htmlFor="targetProcess">
									{t('operate.processes.migration.target')}
								</MigrationHeaderLabel>
								<ComboBox
									id="targetProcess"
									aria-label={t('operate.processes.migration.target')}
									title={t('operate.processes.migration.target')}
									placeholder={t('operate.processes.migration.searchProcess')}
									items={processItems}
									itemToString={(item) => item?.label ?? ''}
									selectedItem={
										processItems.find(
											({definition}) => definition.processDefinitionId === target?.processDefinitionId,
										) ?? null
									}
									shouldFilterItem={({inputValue, item}) =>
										inputValue !== null && item.label.toLowerCase().includes(inputValue.toLowerCase())
									}
									disabled={processItems.length === 0}
									size="sm"
									onChange={({selectedItem}) => {
										if (selectedItem === undefined) {
											return;
										}
										onTargetChange(selectedItem?.definition ?? null);
									}}
								/>
							</Stack>
							<MigrationHeaderField>
								<MigrationHeaderLabel>{t('operate.processes.migration.version')}</MigrationHeaderLabel>
								<Dropdown
									id="targetProcessVersion"
									label="-"
									titleText={t('operate.processes.migration.targetVersion')}
									hideLabel
									type="inline"
									items={versions}
									selectedItem={target?.version ?? null}
									disabled={target === null && versions.length === 0}
									size="sm"
									onChange={({selectedItem}) => {
										onTargetChange(targetVersions?.find(({version}) => version === selectedItem) ?? null);
									}}
								/>
							</MigrationHeaderField>
						</MigrationDiagramHeader>
					)}
					<DiagramShell
						status={targetStatus === 'content' && target === null ? 'empty' : targetStatus}
						emptyMessage={{message: t('operate.processes.migration.selectTarget')}}
						messagePosition="center"
					>
						{targetXml.data?.xml && targetElements !== undefined && (
							<Diagram
								key={target?.processDefinitionKey}
								xml={targetXml.data.xml}
								selectableElements={getSelectableElementIds(targetElements)}
								selectedElementIds={selectedTargetElementId ? [selectedTargetElementId] : undefined}
								onElementSelection={onTargetElementSelection}
								overlaysData={isSummaryStep ? targetOverlays : undefined}
							>
								<StatisticsOverlays />
							</Diagram>
						)}
					</DiagramShell>
				</Section>
			</ResizablePanel>
		</Container>
	);
}

export {MigrationDiagrams};
