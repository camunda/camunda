/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createPortal} from 'react-dom';
import {Trans, useTranslation} from 'react-i18next';
import {Checkbox, Link, Modal, Stack, TableBatchAction} from '@carbon/react';
import {MigrateAlt} from '@carbon/react/icons';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import type {ProcessDefinitionSelection} from './DiagramPanel';
import type {ProcessInstancesSelection} from './useProcessInstancesSelection';
import type {ProcessesMode} from './ProcessesLayout';
import {useDiagramXml} from './useDiagramXml';
import {MigrationHelperList, MigrationHelperListItem} from './styled';
import {getLegacySharedStateFlag} from './getLegacySharedStateFlag';

const HELPER_STORAGE_KEY = 'operate.hideMigrationHelperModal';

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
					if (getStateLocally(HELPER_STORAGE_KEY) ?? getLegacySharedStateFlag('hideMigrationHelperModal')) {
						onEnter();
					} else {
						setIsHelperOpen(true);
					}
				}}
			>
				{t('operate.processes.migration.migrate')}
			</TableBatchAction>
			{createPortal(
				<Modal
					open={isHelperOpen}
					preventCloseOnClickOutside
					modalHeading={t('operate.processes.migration.helperTitle')}
					primaryButtonText={t('operate.processes.batchModification.continue')}
					secondaryButtonText={t('operate.processes.toolbar.cancel')}
					onRequestClose={() => setIsHelperOpen(false)}
					onSecondarySubmit={() => setIsHelperOpen(false)}
					onRequestSubmit={() => {
						setIsHelperOpen(false);
						onEnter();
					}}
					size="md"
				>
					<Stack gap={5}>
						<MigrationHelperList nested>
							<MigrationHelperListItem>{t('operate.processes.migration.helperPurpose')}</MigrationHelperListItem>
							<MigrationHelperListItem>{t('operate.processes.migration.helperImpact')}</MigrationHelperListItem>
							<MigrationHelperListItem>{t('operate.processes.migration.helperPlanning')}</MigrationHelperListItem>
						</MigrationHelperList>
						<p>
							<Trans
								i18nKey="operate.processes.migration.helperDocumentation"
								components={{
									docs: (
										<Link
											href="https://docs.camunda.io/docs/components/operate/userguide/process-instance-migration/"
											target="_blank"
											inline
										/>
									),
								}}
							/>
						</p>
						<Checkbox
							id="hide-migration-helper"
							labelText={t('operate.processes.batchModification.hideHelper')}
							onChange={(_, {checked}) => storeStateLocally(HELPER_STORAGE_KEY, checked)}
						/>
					</Stack>
				</Modal>,
				document.getElementById('main-content') ?? document.body,
			)}
		</>
	);
}

export {MigrateAction};
