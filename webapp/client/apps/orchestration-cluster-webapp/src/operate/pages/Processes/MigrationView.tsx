/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {useBlocker} from '@tanstack/react-router';
import {Modal} from '@carbon/react';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {ProcessesLayout} from './ProcessesLayout';
import {MigrationDiagrams} from './MigrationDiagrams';
import {MigrationMappingTable} from './MigrationMappingTable';
import {MigrationFooter, type MigrationStep} from './MigrationFooter';
import {MigrationDetails} from './MigrationDetails';
import {useDiagramXml} from './useDiagramXml';
import {useInitialMigrationTarget} from './useMigrationTargetDefinitions';
import {useMigrationStatistics} from './useMigrationStatistics';
import {getAutoMapping, getMigrationElements} from './migrationMapping';
import {getSourceSummaryOverlays, getTargetSummaryOverlays} from './migrationSummaryOverlays';
import type {MigrationScope} from './getMigrationFilter';
import {MigrationSummary, MigrationSummaryNotification} from './styled';

type Props = {
	source: ProcessDefinition;
	scope: MigrationScope;
	onExit: () => void;
};

function MigrationView({source, scope, onExit}: Props) {
	const {t} = useTranslation();
	const [target, setTarget] = useState<ProcessDefinition | null>();
	const [editedMapping, setEditedMapping] = useState<Record<string, string>>();
	const [selectedSourceElementId, setSelectedSourceElementId] = useState<string>();
	const [selectedTargetElementId, setSelectedTargetElementId] = useState<string>();
	const [step, setStep] = useState<MigrationStep>('elementMapping');
	const {data: initialTarget} = useInitialMigrationTarget(source);
	if (target === undefined && initialTarget !== undefined) {
		setTarget(initialTarget);
	}
	const targetDefinition = target ?? null;
	const sourceXml = useDiagramXml(source.processDefinitionKey);
	const targetXml = useDiagramXml(targetDefinition?.processDefinitionKey);
	const sourceElements = useMemo(
		() => getMigrationElements(sourceXml.data?.diagramModel, source.processDefinitionId),
		[sourceXml.data, source.processDefinitionId],
	);
	const targetElements = useMemo(
		() =>
			targetDefinition === null || targetXml.data?.diagramModel === undefined
				? undefined
				: getMigrationElements(targetXml.data.diagramModel, targetDefinition.processDefinitionId),
		[targetXml.data, targetDefinition],
	);
	const autoMapping = useMemo(
		() =>
			sourceXml.data?.diagramModel === undefined || targetElements === undefined
				? {}
				: getAutoMapping(sourceElements, targetElements),
		[sourceXml.data, sourceElements, targetElements],
	);
	const mapping = editedMapping ?? autoMapping;
	const isSummaryStep = step === 'summary';
	const {data: statistics} = useMigrationStatistics({
		processDefinitionKey: source.processDefinitionKey,
		filter: scope.statisticsFilter,
		enabled: scope.selectedCount > 0,
	});
	const sourceBusinessObjects = sourceXml.data?.businessObjects;
	const sourceOverlays = useMemo(
		() =>
			statistics === undefined || sourceBusinessObjects === undefined
				? []
				: getSourceSummaryOverlays(statistics, sourceBusinessObjects),
		[statistics, sourceBusinessObjects],
	);
	const targetOverlays = useMemo(
		() => (statistics === undefined ? [] : getTargetSummaryOverlays(statistics, mapping)),
		[statistics, mapping],
	);
	const blocker = useBlocker({
		shouldBlockFn: ({current, next}) =>
			current.pathname !== next.pathname || JSON.stringify(current.search) !== JSON.stringify(next.search),
		withResolver: true,
	});

	const selectSourceElement = (elementId?: string) => {
		setSelectedTargetElementId(undefined);
		setSelectedSourceElementId(selectedSourceElementId === elementId ? undefined : elementId);
	};
	const selectTargetElement = (elementId?: string) => {
		setSelectedSourceElementId(undefined);
		setSelectedTargetElementId(selectedTargetElementId === elementId ? undefined : elementId);
	};
	const changeTarget = (definition: ProcessDefinition | null) => {
		setEditedMapping(undefined);
		setTarget(definition);
	};
	const updateMapping = (sourceElementId: string, targetElementId: string) => {
		const {[sourceElementId]: _, ...rest} = mapping;
		setEditedMapping(targetElementId === '' ? rest : {...rest, [sourceElementId]: targetElementId});
	};
	const getMappedSourceElementIds = (targetElementId: string) =>
		Object.keys(mapping).filter((sourceElementId) => mapping[sourceElementId] === targetElementId);
	const selectedSourceElementIds =
		selectedSourceElementId !== undefined
			? mapping[selectedSourceElementId] !== undefined
				? getMappedSourceElementIds(mapping[selectedSourceElementId])
				: [selectedSourceElementId]
			: selectedTargetElementId !== undefined
				? getMappedSourceElementIds(selectedTargetElementId)
				: undefined;
	const highlightedTargetElementId =
		selectedTargetElementId ?? (selectedSourceElementId === undefined ? undefined : mapping[selectedSourceElementId]);

	return (
		<ProcessesLayout
			type="migrate"
			title={t('operate.processes.migration.pageTitle')}
			frame={{
				headerTitle: t(
					isSummaryStep ? 'operate.processes.migration.summaryFrameTitle' : 'operate.processes.migration.frameTitle',
				),
			}}
			additionalTopContent={
				isSummaryStep && targetDefinition !== null ? (
					<MigrationSummaryNotification kind="info" title="" lowContrast hideCloseButton>
						<MigrationSummary orientation="vertical" gap={5}>
							<MigrationDetails source={source} target={targetDefinition} scope={scope} />
							<p>{t('operate.processes.migration.summaryProgress')}</p>
							<p>{t('operate.processes.migration.summaryMapping')}</p>
						</MigrationSummary>
					</MigrationSummaryNotification>
				) : undefined
			}
			topPanel={
				<MigrationDiagrams
					source={source}
					target={targetDefinition}
					sourceXml={sourceXml}
					targetXml={targetXml}
					sourceElements={sourceElements}
					targetElements={targetElements}
					selectedSourceElementIds={selectedSourceElementIds}
					selectedTargetElementId={highlightedTargetElementId}
					onSourceElementSelection={selectSourceElement}
					onTargetElementSelection={selectTargetElement}
					onTargetChange={changeTarget}
					isSummaryStep={isSummaryStep}
					sourceOverlays={sourceOverlays}
					targetOverlays={targetOverlays}
				/>
			}
			bottomPanel={
				<MigrationMappingTable
					isTargetSelected={targetDefinition !== null}
					isSummaryStep={isSummaryStep}
					hasSourceXml={sourceXml.data?.diagramModel !== undefined}
					sourceElements={sourceElements}
					targetElements={targetElements}
					mapping={mapping}
					autoMapping={autoMapping}
					selectedSourceElementIds={selectedSourceElementIds}
					onSourceElementSelection={selectSourceElement}
					onMappingChange={updateMapping}
				/>
			}
			footer={
				<>
					<MigrationFooter
						source={source}
						target={targetDefinition}
						scope={scope}
						mapping={mapping}
						step={step}
						onStepChange={setStep}
						onExit={onExit}
					/>
					{createPortal(
						<Modal
							open={blocker.status === 'blocked'}
							modalHeading={t('operate.processes.migration.leaveTitle')}
							preventCloseOnClickOutside
							onRequestClose={() => blocker.reset?.()}
							secondaryButtonText={t('operate.processes.migration.stay')}
							primaryButtonText={t('operate.processes.migration.leave')}
							onRequestSubmit={() => {
								blocker.proceed?.();
								onExit();
							}}
						>
							<p>{t('operate.processes.migration.leaveDiscard')}</p>
						</Modal>,
						document.getElementById('main-content') ?? document.body,
					)}
				</>
			}
		/>
	);
}

export {MigrationView};
