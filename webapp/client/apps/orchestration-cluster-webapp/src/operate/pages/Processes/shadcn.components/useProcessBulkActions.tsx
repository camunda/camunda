/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {CircleX, Pause, Play, RotateCcw, Trash2} from '@camunda/design-system/icons';
import type {ProcessInstancesSelection, ProcessBulkAction} from '../useProcessInstancesSelection';
import type {ProcessBulkActionDialogProps} from './ProcessBulkActionDialog';
import {ENABLE_PROCESS_MIGRATION} from '#/shared/feature-flags';
import {useMigrateAction} from './useMigrateAction';
import type {ProcessesMode} from './processesMode';
import type {MoveBatchAction} from './useMoveBatchAction';

type Props = {
	selection: ProcessInstancesSelection;
	isSubmitting: boolean;
	onSubmit: (action: ProcessBulkAction) => void;
	migration: Omit<Parameters<typeof useMigrateAction>[0], 'selection' | 'isSubmitting'>;
	mode?: ProcessesMode;
	moveAction?: MoveBatchAction;
};

const UNAVAILABLE_REASON_KEYS = {
	delete: 'noFinished',
	cancel: 'noRunning',
	retry: 'noIncidents',
	suspend: 'noRunningToSuspend',
	resume: 'noSuspended',
} as const;

const ACTIONS = [
	{action: 'delete', Icon: Trash2},
	{action: 'cancel', Icon: CircleX},
	{action: 'retry', Icon: RotateCcw},
	{action: 'suspend', Icon: Pause},
	{action: 'resume', Icon: Play},
] as const;

function useProcessBulkActions({selection, isSubmitting, onSubmit, migration, mode = 'list', moveAction}: Props) {
	const {t} = useTranslation();
	const migrateAction = useMigrateAction({...migration, selection, isSubmitting});
	const [dialog, setDialog] = useState<{action: ProcessBulkAction; identity: string} | null>(null);
	const {selectedCount, isCountTruncated, filterIdentity, eligibility} = selection;
	const action = dialog?.identity === filterIdentity && selectedCount > 0 ? dialog.action : null;
	const staleDialog = dialog !== null && (dialog.identity !== filterIdentity || selectedCount <= 0);
	if (staleDialog) {
		setDialog(null);
	}
	const total = `${selectedCount}${isCountTruncated ? '+' : ''}`;

	const isActionMode = mode !== 'list';

	const operationActions = ACTIONS.map(({action: operation, Icon}) => ({
		id: operation,
		label: t(`operate.processes.toolbar.${operation}`),
		variant: operation === 'delete' ? ('destructive' as const) : ('default' as const),
		icon: <Icon aria-hidden />,
		disabled: isSubmitting || isActionMode || !eligibility[operation],
		onClick: () => setDialog({action: operation, identity: filterIdentity}),
	}));
	const migrationAction = {
		...migrateAction.batchAction,
		disabled: isActionMode || migrateAction.batchAction.disabled,
	};
	const batchActions = [
		...(moveAction === undefined ? [] : [moveAction.action]),
		...operationActions,
		...(ENABLE_PROCESS_MIGRATION ? [migrationAction] : []),
	];
	const unavailableReasons = isActionMode
		? [t('operate.processes.toolbar.actionModeDs')]
		: [
				...(moveAction?.disabledReason === undefined ? [] : [moveAction.disabledReason]),
				...ACTIONS.filter(({action: operation}) => !eligibility[operation]).map(({action: operation}) =>
					t(`operate.processes.toolbar.${UNAVAILABLE_REASON_KEYS[operation]}`),
				),
				...(!ENABLE_PROCESS_MIGRATION || migrateAction.disabledReason === undefined
					? []
					: [migrateAction.disabledReason]),
			];
	const selectedLabel = t('operate.processes.ds.toolbar.selected', {count: selectedCount, total});
	const unavailableText = unavailableReasons.join(' ');
	// Reasons stay out of the visible bar but remain available on hover and to screen readers.
	const selectionText = (
		<>
			{selectedLabel}
			{unavailableText ? <span className="sr-only"> {unavailableText}</span> : null}
		</>
	);
	const selectionCountLabel = unavailableText ? (
		<Tooltip>
			<TooltipTrigger asChild>
				<span tabIndex={0}>{selectionText}</span>
			</TooltipTrigger>
			<TooltipContent>{unavailableText}</TooltipContent>
		</Tooltip>
	) : (
		<span>{selectionText}</span>
	);

	const dialogProps: ProcessBulkActionDialogProps = {
		action,
		selection,
		isSubmitting,
		onOpenChange: (open) => {
			if (!open) {
				setDialog(null);
			}
		},
		onCancel: selection.reset,
		onConfirm: () => {
			if (action !== null && selectedCount > 0 && !isSubmitting && eligibility[action]) {
				onSubmit(action);
				setDialog(null);
			}
		},
	};

	return {
		batchActions,
		selectionCountLabel,
		dialogProps,
		migrationHelper: migrateAction.helper,
	};
}

export {useProcessBulkActions};
