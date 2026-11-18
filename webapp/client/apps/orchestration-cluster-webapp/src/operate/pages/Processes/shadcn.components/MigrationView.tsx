/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useBlocker} from '@tanstack/react-router';
import {
	Alert,
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	AlertDialogDescription,
	Heading,
} from '@camunda/design-system';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {Frame} from '#/operate/shared/Frame/shadcn.components/Frame';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {useDiagramXml} from '../useDiagramXml';
import {useInitialMigrationTarget} from '../useMigrationTargetDefinitions';
import {useMigrationStatistics} from '../useMigrationStatistics';
import {getAutoMapping, getMigrationElements} from '../migrationMapping';
import {getSourceSummaryOverlays, getTargetSummaryOverlays} from '../migrationSummaryOverlays';
import type {MigrationScope} from '../getMigrationFilter';
import {MigrationDiagrams} from './MigrationDiagrams';
import {MigrationMappingTable} from './MigrationMappingTable';
import {MigrationFooter, type MigrationStep} from './MigrationFooter';
import {MigrationDetails} from './MigrationDetails';

type Props = {
	source: ProcessDefinition;
	scope: MigrationScope;
	onExit: () => void;
};

const MigrationView: React.FC<Props> = ({source, scope, onExit}) => {
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
	const mapping = useMemo(
		() => Object.assign(Object.create(null), editedMapping ?? autoMapping),
		[editedMapping, autoMapping],
	);
	const isSummaryStep = step === 'summary';
	const statistics = useMigrationStatistics({
		processDefinitionKey: source.processDefinitionKey,
		filter: scope.statisticsFilter,
		enabled: isSummaryStep && scope.selectedCount > 0,
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
	const selectedSourceElementIds = useMemo(() => {
		const getMappedSourceElementIds = (targetElementId: string) =>
			Object.keys(mapping).filter((sourceElementId) => mapping[sourceElementId] === targetElementId);
		if (selectedSourceElementId !== undefined) {
			return mapping[selectedSourceElementId] !== undefined
				? getMappedSourceElementIds(mapping[selectedSourceElementId])
				: [selectedSourceElementId];
		}
		return selectedTargetElementId !== undefined ? getMappedSourceElementIds(selectedTargetElementId) : undefined;
	}, [mapping, selectedSourceElementId, selectedTargetElementId]);
	const highlightedTargetElementId =
		selectedTargetElementId ?? (selectedSourceElementId === undefined ? undefined : mapping[selectedSourceElementId]);

	return (
		<div className="flex h-full min-w-0 flex-1 flex-col">
			<Heading as="h1" className="sr-only">
				{t('operate.processes.migration.pageTitle')}
			</Heading>
			<div className="min-h-0 flex-1">
				<Frame
					frame={{
						headerTitle: t(
							isSummaryStep
								? 'operate.processes.migration.summaryFrameTitle'
								: 'operate.processes.migration.frameTitle',
						),
					}}
				>
					<div className="flex h-full min-h-0 flex-col">
						{isSummaryStep && targetDefinition !== null && (
							<Alert
								variant="info"
								className="m-2 w-auto"
								description={
									<div className="flex flex-col gap-3">
										<MigrationDetails source={source} target={targetDefinition} scope={scope} />
										<p>{t('operate.processes.migration.summaryProgress')}</p>
										<p>{t('operate.processes.migration.summaryMapping')}</p>
									</div>
								}
							/>
						)}
						<div className="min-h-0 flex-1">
							<ResizablePanel
								panelId="process-migration-vertical-panel"
								direction={SplitDirection.Vertical}
								minHeights={[150, 150]}
							>
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
							</ResizablePanel>
						</div>
						<MigrationFooter
							source={source}
							target={targetDefinition}
							scope={scope}
							mapping={mapping}
							step={step}
							onStepChange={setStep}
							onExit={onExit}
						/>
					</div>
				</Frame>
			</div>
			<AlertDialog
				open={blocker.status === 'blocked'}
				onOpenChange={(open) => {
					if (!open) {
						blocker.reset?.();
					}
				}}
			>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{t('operate.processes.migration.leaveTitle')}</AlertDialogTitle>
					</AlertDialogHeader>
					<AlertDialogDescription>{t('operate.processes.migration.leaveDiscard')}</AlertDialogDescription>
					<AlertDialogFooter>
						<AlertDialogCancel>{t('operate.processes.migration.stay')}</AlertDialogCancel>
						<AlertDialogAction
							onClick={() => {
								blocker.proceed?.();
								onExit();
							}}
						>
							{t('operate.processes.migration.leave')}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</div>
	);
};

export {MigrationView};
