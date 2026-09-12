/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, type ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import {Modal, TableBatchAction, TableBatchActions, TableToolbar} from '@carbon/react';
import {Error, Pause, Play, RetryFailed, TrashCan} from '@carbon/react/icons';
import type {ProcessInstancesSelection, ProcessBulkAction} from './useProcessInstancesSelection';

type Props = {
	selection: ProcessInstancesSelection;
	isSubmitting: boolean;
	isActionMode?: boolean;
	additionalActions?: ReactNode;
	onSubmit: (action: ProcessBulkAction) => void;
};
const ICONS = {delete: TrashCan, cancel: Error, retry: RetryFailed, suspend: Pause, resume: Play};
const ACTIONS = ['delete', 'cancel', 'retry', 'suspend', 'resume'] as const;

function ProcessesToolbar({selection, isSubmitting, isActionMode, additionalActions, onSubmit}: Props) {
	const {t} = useTranslation();
	const [modal, setModal] = useState<{action: ProcessBulkAction; identity: string} | null>(null);
	const {selectedCount, isCountTruncated} = selection;
	const action = modal?.identity === selection.filterIdentity ? modal.action : null;
	const labels = {
		delete: t('operate.processes.toolbar.delete'),
		cancel: t('operate.processes.toolbar.cancel'),
		retry: t('operate.processes.toolbar.retry'),
		suspend: t('operate.processes.toolbar.suspend'),
		resume: t('operate.processes.toolbar.resume'),
	};
	const disabledTitles = {
		delete: t('operate.processes.toolbar.noFinished'),
		cancel: t('operate.processes.toolbar.noRunning'),
		retry: t('operate.processes.toolbar.noIncidents'),
		suspend: t('operate.processes.toolbar.noRunningToSuspend'),
		resume: t('operate.processes.toolbar.noSuspended'),
	};
	const count = `${selectedCount}${isCountTruncated ? '+' : ''}`;

	if (selectedCount === 0) {
		return null;
	}

	return (
		<>
			<TableToolbar size="sm">
				<TableBatchActions
					shouldShowBatchActions
					totalSelected={selectedCount}
					onCancel={selection.reset}
					translateWithId={(id) => {
						switch (id) {
							case 'carbon.table.batch.cancel':
								return t('operate.processes.toolbar.discard');
							case 'carbon.table.batch.items.selected':
							case 'carbon.table.batch.item.selected':
								return t('operate.processes.toolbar.selected', {count: selectedCount, total: count});
							case 'carbon.table.batch.selectAll':
								return t('operate.processes.toolbar.selectAll');
							default:
								return id;
						}
					}}
				>
					{additionalActions}
					{ACTIONS.map((operation) => (
						<TableBatchAction
							key={operation}
							renderIcon={ICONS[operation]}
							disabled={isSubmitting || isActionMode || !selection.eligibility[operation]}
							title={
								isActionMode
									? t('operate.processes.toolbar.actionMode')
									: !selection.eligibility[operation]
										? disabledTitles[operation]
										: undefined
							}
							onClick={() => setModal({action: operation, identity: selection.filterIdentity})}
						>
							{labels[operation]}
						</TableBatchAction>
					))}
				</TableBatchActions>
			</TableToolbar>
			<Modal
				open={action !== null}
				preventCloseOnClickOutside
				modalHeading={t('operate.processes.toolbar.heading')}
				primaryButtonText={action === 'delete' ? labels.delete : t('operate.processes.toolbar.apply')}
				primaryButtonDisabled={isSubmitting || action === null || !selection.eligibility[action]}
				danger={action === 'delete'}
				secondaryButtonText={labels.cancel}
				onRequestClose={() => setModal(null)}
				onSecondarySubmit={() => {
					setModal(null);
					selection.reset();
				}}
				onRequestSubmit={() => {
					if (action !== null && !isSubmitting && selection.eligibility[action]) {
						onSubmit(action);
						setModal(null);
					}
				}}
				size="md"
			>
				<p>
					{action &&
						t('operate.processes.toolbar.confirm', {
							count: selectedCount,
							total: count,
							action: labels[action],
						})}
					{action === 'delete' && ` ${t('operate.processes.toolbar.permanent')}`}
					{action === 'cancel' && ` ${t('operate.processes.toolbar.calledInstances')}`}
					{action === 'cancel' &&
						selection.mode === 'INCLUDE' &&
						selectedCount > selection.runningCount &&
						` ${t('operate.processes.toolbar.ignoreFinished')}`}
					{action === 'retry' &&
						selection.mode === 'INCLUDE' &&
						selectedCount > selection.incidentCount &&
						` ${t('operate.processes.toolbar.ignoreNonIncident')}`}
					{action === 'suspend' &&
						selection.mode === 'INCLUDE' &&
						selectedCount > selection.runningCount &&
						` ${t('operate.processes.toolbar.ignoreNonActive')}`}
					{action === 'resume' &&
						selection.mode === 'INCLUDE' &&
						selectedCount > selection.suspendedCount &&
						` ${t('operate.processes.toolbar.ignoreNonSuspended')}`}
					{action === 'suspend' &&
						selectedCount > selection.runningCount &&
						` ${t('operate.processes.toolbar.ignoreNonActive')}`}
					{action === 'resume' &&
						selectedCount > selection.suspendedCount &&
						` ${t('operate.processes.toolbar.ignoreNonSuspended')}`}
				</p>
			</Modal>
		</>
	);
}

export {ProcessesToolbar};
