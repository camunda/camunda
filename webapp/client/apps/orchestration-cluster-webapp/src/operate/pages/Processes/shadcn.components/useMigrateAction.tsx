/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {ArrowRightLeft} from '@camunda/design-system/icons';
import {MigrationHelperModal} from '#/operate/shared/MigrationHelperModal/shadcn.components/MigrationHelperModal';
import {isMigrationHelperHidden} from '#/operate/shared/MigrationHelperModal/migrationHelperPreference';
import {getMigrationFilter, getMigrationStatisticsFilter, type MigrationScope} from '../getMigrationFilter';
import {ENABLE_PROCESS_MIGRATION} from '#/shared/feature-flags';
import {useDiagramXml} from '../useDiagramXml';
import type {ProcessesSearch} from '../processesFilter';
import type {ProcessInstancesSelection} from '../useProcessInstancesSelection';
import type {ProcessDefinitionSelection} from './processDefinitionSelection';

type Props = {
	search: ProcessesSearch;
	selection: ProcessInstancesSelection;
	isSubmitting: boolean;
	processDefinitionSelection: ProcessDefinitionSelection;
	onEnter: (
		definition: Extract<ProcessDefinitionSelection, {kind: 'single-version'}>['definition'],
		scope: MigrationScope,
	) => void;
};

function useMigrateAction({search, selection, isSubmitting, processDefinitionSelection, onEnter}: Props) {
	const {t} = useTranslation();
	const [isHelperOpen, setIsHelperOpen] = useState(false);
	const definitionKey =
		ENABLE_PROCESS_MIGRATION && processDefinitionSelection.kind === 'single-version'
			? processDefinitionSelection.definition.processDefinitionKey
			: undefined;
	const {data, isError} = useDiagramXml(definitionKey);
	const includeIds = selection.mode === 'INCLUDE' ? selection.includedIds : [];
	const migrationFilter =
		definitionKey === undefined
			? null
			: getMigrationFilter({
					search,
					includeIds,
					excludeIds: selection.excludedIds,
					processDefinitionKey: definitionKey,
				});
	const disabledReason =
		definitionKey === undefined
			? t('operate.processes.migration.selectSource')
			: isError || data?.xml === ''
				? t('operate.processes.migration.diagramError')
				: !selection.eligibility.running || migrationFilter === null
					? t('operate.processes.migration.runningOnly')
					: undefined;

	const canEnter = disabledReason === undefined && selection.selectedCount > 0;
	if (isHelperOpen && !canEnter) {
		setIsHelperOpen(false);
	}

	const enter = () => {
		if (!canEnter || processDefinitionSelection.kind !== 'single-version' || migrationFilter === null) {
			return;
		}
		onEnter(processDefinitionSelection.definition, {
			filter: migrationFilter,
			statisticsFilter: getMigrationStatisticsFilter({search, includeIds, excludeIds: selection.excludedIds}),
			selectedCount: selection.selectedCount,
			isCountTruncated: selection.isCountTruncated,
		});
	};

	return {
		disabledReason,
		batchAction: {
			id: 'migrate',
			label: t('operate.processes.migration.migrate'),
			variant: 'default' as const,
			icon: <ArrowRightLeft aria-hidden />,
			disabled: isSubmitting || disabledReason !== undefined,
			onClick: () => {
				if (isMigrationHelperHidden()) {
					enter();
				} else {
					setIsHelperOpen(true);
				}
			},
		},
		helper: (
			<MigrationHelperModal
				open={isHelperOpen && canEnter}
				onClose={() => setIsHelperOpen(false)}
				onSubmit={() => {
					setIsHelperOpen(false);
					enter();
				}}
			/>
		),
	};
}

export {useMigrateAction};
