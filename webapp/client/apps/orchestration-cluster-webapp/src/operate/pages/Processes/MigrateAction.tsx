/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {TableBatchAction} from '@carbon/react';
import {MigrateAlt} from '@carbon/react/icons';
import type {ProcessDefinitionSelection} from './DiagramPanel';
import type {ProcessInstancesSelection} from './useProcessInstancesSelection';
import type {ProcessesMode} from './ProcessesLayout';
import {useDiagramXml} from './useDiagramXml';
import {MigrationHelperModal} from './MigrationHelperModal';
import {isMigrationHelperHidden} from './migrationHelperPreference';

type Props = {
	mode: ProcessesMode;
	isSubmitting: boolean;
	hasActiveScope: boolean;
	selection: ProcessInstancesSelection;
	processDefinitionSelection: ProcessDefinitionSelection;
	onEnter: () => void;
};

function MigrateAction({mode, isSubmitting, hasActiveScope, selection, processDefinitionSelection, onEnter}: Props) {
	const {t} = useTranslation();
	const [isHelperOpen, setIsHelperOpen] = useState(false);
	const definitionKey =
		processDefinitionSelection.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const {data, isError} = useDiagramXml(definitionKey);
	// Like legacy, an EXCLUDE selection is judged by the state filters alone (none, or active/incidents), not by which
	// instances the exclusions leave. Do not gate it on a count of the remaining active instances.
	// Like legacy, only a failed or empty diagram request disables Migrate; it stays enabled while the diagram loads.
	const disabledReason =
		mode !== 'list'
			? t('operate.processes.toolbar.actionMode')
			: definitionKey === undefined
				? t('operate.processes.migration.selectSource')
				: isError || data?.xml === ''
					? t('operate.processes.migration.diagramError')
					: !selection.eligibility.cancel || !hasActiveScope
						? t('operate.processes.migration.runningOnly')
						: undefined;

	return (
		<>
			<TableBatchAction
				renderIcon={MigrateAlt}
				disabled={isSubmitting || disabledReason !== undefined}
				title={disabledReason}
				onClick={() => {
					if (isMigrationHelperHidden()) {
						onEnter();
					} else {
						setIsHelperOpen(true);
					}
				}}
			>
				{t('operate.processes.migration.migrate')}
			</TableBatchAction>
			<MigrationHelperModal
				open={isHelperOpen}
				onClose={() => setIsHelperOpen(false)}
				onSubmit={() => {
					setIsHelperOpen(false);
					onEnter();
				}}
			/>
		</>
	);
}

export {MigrateAction};
