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
import {MigrationFooter} from './MigrationFooter';
import {useDiagramXml} from './useDiagramXml';
import {useInitialMigrationTarget} from './useMigrationTargetDefinitions';
import {getAutoMapping, getMigrationElements} from './migrationMapping';

type Props = {
	source: ProcessDefinition;
	onExit: () => void;
};

function MigrationView({source, onExit}: Props) {
	const {t} = useTranslation();
	const [target, setTarget] = useState<ProcessDefinition | null>();
	const [editedMapping, setEditedMapping] = useState<Record<string, string>>();
	const [selectedSourceElementId, setSelectedSourceElementId] = useState<string>();
	const [selectedTargetElementId, setSelectedTargetElementId] = useState<string>();
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
			frame={{headerTitle: t('operate.processes.migration.frameTitle')}}
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
				/>
			}
			bottomPanel={
				<MigrationMappingTable
					isTargetSelected={targetDefinition !== null}
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
					<MigrationFooter onExit={onExit} />
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
