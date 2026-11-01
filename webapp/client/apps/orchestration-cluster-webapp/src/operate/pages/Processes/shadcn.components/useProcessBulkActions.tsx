/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {CircleX, Pause, Play, RotateCcw, Trash2} from '@camunda/design-system/icons';
import type {ProcessInstancesSelection, ProcessBulkAction} from '../useProcessInstancesSelection';
import type {ProcessBulkActionDialogProps} from './ProcessBulkActionDialog';

type Props = {
	selection: ProcessInstancesSelection;
	isSubmitting: boolean;
	onSubmit: (action: ProcessBulkAction) => void;
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

function useProcessBulkActions({selection, isSubmitting, onSubmit}: Props) {
	const {t} = useTranslation();
	const [dialog, setDialog] = useState<{action: ProcessBulkAction; identity: string} | null>(null);
	const {selectedCount, isCountTruncated, filterIdentity, eligibility} = selection;
	const action = dialog?.identity === filterIdentity && selectedCount > 0 ? dialog.action : null;
	const staleDialog = dialog !== null && (dialog.identity !== filterIdentity || selectedCount === 0);
	if (staleDialog) {
		setDialog(null);
	}
	const total = `${selectedCount}${isCountTruncated ? '+' : ''}`;

	const batchActions = ACTIONS.map(({action: operation, Icon}) => ({
		id: operation,
		label: t(`operate.processes.toolbar.${operation}`),
		variant: operation === 'delete' ? ('destructive' as const) : ('default' as const),
		icon: <Icon aria-hidden />,
		disabled: isSubmitting || !eligibility[operation],
		onClick: () => setDialog({action: operation, identity: filterIdentity}),
	}));

	const unavailableReasons = ACTIONS.filter(({action: operation}) => !eligibility[operation]).map(
		({action: operation}) => t(`operate.processes.toolbar.${UNAVAILABLE_REASON_KEYS[operation]}`),
	);
	const selectionCountLabel = [
		t('operate.processes.toolbar.selected', {count: selectedCount, total}),
		...unavailableReasons,
	].join(' ');

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
	};
}

export {useProcessBulkActions};
