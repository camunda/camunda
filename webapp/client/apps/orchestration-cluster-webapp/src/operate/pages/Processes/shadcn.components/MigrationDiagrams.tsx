/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	Alert,
	Button,
	Combobox,
	Label,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from '@camunda/design-system';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {Diagram} from '#/operate/shared/Diagram/shadcn.components/Diagram';
import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';
import {DiagramShell} from '#/operate/shared/DiagramShell/shadcn.components/DiagramShell';
import {ResizablePanel, SplitDirection} from '#/operate/shared/ResizablePanel/shadcn.components/ResizablePanel';
import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {getProcessDefinitionName} from '../getProcessDefinitionName';
import type {MigrationElements} from '../migrationMapping';
import type {useDiagramXml} from '../useDiagramXml';
import {useAvailableMigrationTargets, useMigrationTargetVersions} from '../useMigrationTargetDefinitions';
import {anchorStateOverlaysToCorners} from '#/operate/shared/StateOverlay/shadcn.components/anchorStateOverlaysToCorners';
import {StatisticsOverlays} from './DiagramPanel';

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

function getDiagramStatus({isLoading, isError, data}: DiagramXmlQuery, failedXml?: string) {
	if (isLoading) {
		return 'loading';
	}
	return isError || data?.xml === '' || (failedXml !== undefined && failedXml === data?.xml) ? 'error' : 'content';
}

const HeaderField: React.FC<{label: string; htmlFor?: string; children: React.ReactNode}> = ({
	label,
	htmlFor,
	children,
}) => (
	<div className="flex min-w-0 items-center gap-3">
		<Label htmlFor={htmlFor} className="font-bold">
			{label}
		</Label>
		{children}
	</div>
);

const MigrationDiagrams: React.FC<Props> = ({
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
}) => {
	const {t} = useTranslation();
	const {targets: availableTargets, ...targetsQuery} = useAvailableMigrationTargets(source);
	const versionsQuery = useMigrationTargetVersions(source, target);
	const {data: targetVersions} = versionsQuery;
	const hasTargetsError = targetsQuery.isError || versionsQuery.isError;
	const isRetryingTargets = targetsQuery.isFetching || versionsQuery.isFetching;
	const retryTargets = () => {
		if (targetsQuery.isError) {
			void targetsQuery.refetch();
		}
		if (versionsQuery.isError) {
			void versionsQuery.refetch();
		}
	};
	const containerRef = useRef<HTMLDivElement>(null);
	const [clientWidth, setClientWidth] = useState(0);
	const panelMinWidth = clientWidth / 3;

	useEffect(() => {
		const container = containerRef.current;
		if (container === null) {
			return;
		}
		const observer = new ResizeObserver(() => setClientWidth(container.clientWidth));
		observer.observe(container);
		return () => observer.disconnect();
	}, []);
	const processOptions = useMemo(
		() =>
			availableTargets.map((definition) => ({
				value: definition.processDefinitionKey,
				label: getProcessDefinitionName(definition),
			})),
		[availableTargets],
	);
	const selectedProcessKey = availableTargets.find(
		({processDefinitionId}) => processDefinitionId === target?.processDefinitionId,
	)?.processDefinitionKey;
	const [failedSourceXml, setFailedSourceXml] = useState<string>();
	const [failedTargetXml, setFailedTargetXml] = useState<string>();
	const targetStatus = getDiagramStatus(targetXml, failedTargetXml);
	const sourceSelectableIds = useMemo(() => getSelectableElementIds(sourceElements), [sourceElements]);
	const targetSelectableIds = useMemo(
		() => (targetElements === undefined ? undefined : getSelectableElementIds(targetElements)),
		[targetElements],
	);
	const selectedTargetElementIds = useMemo(
		() => (selectedTargetElementId ? [selectedTargetElementId] : undefined),
		[selectedTargetElementId],
	);
	const anchoredSourceOverlays = useMemo(() => anchorStateOverlaysToCorners(sourceOverlays), [sourceOverlays]);
	const anchoredTargetOverlays = useMemo(() => anchorStateOverlaysToCorners(targetOverlays), [targetOverlays]);

	return (
		<div ref={containerRef} className="h-full">
			<ResizablePanel
				panelId="process-migration-diagrams-horizontal-panel"
				direction={SplitDirection.Horizontal}
				minWidths={[panelMinWidth, panelMinWidth]}
			>
				<section className="flex h-full min-w-0 flex-col">
					{!isSummaryStep && (
						<div className="flex h-12 items-center gap-6 border-b px-4 text-sm">
							<HeaderField label={t('operate.processes.migration.source')}>
								<TruncatedText text={getProcessDefinitionName(source)} className="min-w-0" />
							</HeaderField>
							<HeaderField label={t('operate.processes.migration.version')}>
								<span>{source.version}</span>
							</HeaderField>
						</div>
					)}
					<div className="isolate min-h-0 flex-1">
						<DiagramShell
							status={getDiagramStatus(sourceXml, failedSourceXml)}
							onRetry={sourceXml.isError ? () => void sourceXml.refetch() : undefined}
							contentLabel={t('operate.processes.migration.source')}
						>
							{sourceXml.data?.xml && (
								<Diagram
									ariaLabel={`${t('operate.processes.migration.source')} ${t('operate.shared.diagram.label')}`}
									xml={sourceXml.data.xml}
									selectableElements={sourceSelectableIds}
									selectedElementIds={selectedSourceElementIds}
									onElementSelection={onSourceElementSelection}
									overlaysData={isSummaryStep ? anchoredSourceOverlays : undefined}
									onRenderError={() => setFailedSourceXml(sourceXml.data?.xml)}
								>
									<StatisticsOverlays businessObjects={sourceXml.data.businessObjects} />
								</Diagram>
							)}
						</DiagramShell>
					</div>
				</section>
				<section className="flex h-full min-w-0 flex-col">
					{!isSummaryStep && (
						<div className="flex h-12 items-center gap-6 border-b px-4 text-sm">
							<HeaderField label={t('operate.processes.migration.target')} htmlFor="targetProcess">
								<Combobox
									id="targetProcess"
									size="sm"
									aria-label={t('operate.processes.migration.target')}
									placeholder={t('operate.processes.migration.searchProcess')}
									searchPlaceholder={t('operate.processes.migration.searchProcess')}
									options={processOptions}
									value={selectedProcessKey}
									disabled={processOptions.length === 0}
									onValueChange={(value) => {
										onTargetChange(
											availableTargets.find(({processDefinitionKey}) => processDefinitionKey === value) ?? null,
										);
									}}
								/>
							</HeaderField>
							<HeaderField label={t('operate.processes.migration.version')}>
								<Select
									value={target === null ? '' : String(target.version)}
									disabled={target === null && (targetVersions?.length ?? 0) === 0}
									onValueChange={(value) => {
										onTargetChange(targetVersions?.find(({version}) => String(version) === value) ?? null);
									}}
								>
									<SelectTrigger size="sm" aria-label={t('operate.processes.migration.targetVersion')}>
										<SelectValue placeholder="-" />
									</SelectTrigger>
									<SelectContent>
										{(targetVersions ?? []).map(({version}) => (
											<SelectItem key={version} value={String(version)}>
												{version}
											</SelectItem>
										))}
									</SelectContent>
								</Select>
							</HeaderField>
						</div>
					)}
					{!isSummaryStep && hasTargetsError && (
						<Alert
							variant="destructive"
							title={t('operate.processes.migration.targetsError')}
							className="rounded-none border-x-0"
						>
							<Button variant="secondary" size="sm" loading={isRetryingTargets} onClick={retryTargets}>
								{t('operate.processes.migration.retry')}
							</Button>
						</Alert>
					)}
					<div className="isolate min-h-0 flex-1">
						<DiagramShell
							status={targetStatus === 'content' && target === null ? 'empty' : targetStatus}
							contentLabel={t('operate.processes.migration.target')}
							onRetry={targetXml.isError ? () => void targetXml.refetch() : undefined}
							emptyMessage={{
								heading: t('operate.processes.migration.selectTarget'),
								description: t('operate.processes.ds.migration.selectTargetDescription'),
							}}
							messagePosition="center"
						>
							{targetXml.data?.xml && targetElements !== undefined && (
								<Diagram
									key={target?.processDefinitionKey}
									ariaLabel={`${t('operate.processes.migration.target')} ${t('operate.shared.diagram.label')}`}
									xml={targetXml.data.xml}
									selectableElements={targetSelectableIds}
									selectedElementIds={selectedTargetElementIds}
									onElementSelection={onTargetElementSelection}
									overlaysData={isSummaryStep ? anchoredTargetOverlays : undefined}
									onRenderError={() => setFailedTargetXml(targetXml.data?.xml)}
								>
									<StatisticsOverlays businessObjects={targetXml.data.businessObjects} />
								</Diagram>
							)}
						</DiagramShell>
					</div>
				</section>
			</ResizablePanel>
		</div>
	);
};

export {MigrationDiagrams};
