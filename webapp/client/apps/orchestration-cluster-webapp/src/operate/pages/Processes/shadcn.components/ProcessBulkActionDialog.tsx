/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {BulkActionConfirmDialog} from '#/operate/shared/BulkActionBar/shadcn.components/BulkActionConfirmDialog';
import type {ProcessInstancesSelection, ProcessBulkAction} from '../useProcessInstancesSelection';

type ProcessBulkActionDialogProps = {
	action: ProcessBulkAction | null;
	selection: ProcessInstancesSelection;
	isSubmitting: boolean;
	onOpenChange: (open: boolean) => void;
	onCancel: () => void;
	onConfirm: () => void;
};

const ProcessBulkActionDialog: React.FC<ProcessBulkActionDialogProps> = ({
	action,
	selection,
	isSubmitting,
	onOpenChange,
	onCancel,
	onConfirm,
}) => {
	const {t} = useTranslation();
	const {selectedCount, isCountTruncated, mode, runningCount, suspendedCount, incidentCount} = selection;
	const total = `${selectedCount}${isCountTruncated ? '+' : ''}`;
	const isInclude = mode === 'INCLUDE';
	const notes = [
		action === 'delete' && t('operate.processes.toolbar.permanent'),
		action === 'cancel' && t('operate.processes.toolbar.calledInstances'),
		action === 'cancel' &&
			isInclude &&
			selectedCount > runningCount + suspendedCount &&
			t('operate.processes.toolbar.ignoreFinished'),
		action === 'retry' &&
			isInclude &&
			selectedCount > incidentCount &&
			t('operate.processes.toolbar.ignoreNonIncident'),
		action === 'suspend' && isInclude && selectedCount > runningCount && t('operate.processes.toolbar.ignoreNonActive'),
		action === 'resume' &&
			isInclude &&
			selectedCount > suspendedCount &&
			t('operate.processes.toolbar.ignoreNonSuspended'),
	].filter(Boolean);

	return (
		<BulkActionConfirmDialog
			open={action !== null}
			title={t('operate.processes.toolbar.heading')}
			description={
				action !== null &&
				[
					t('operate.processes.toolbar.confirm', {
						count: selectedCount,
						total,
						action: t(`operate.processes.toolbar.${action}`),
					}),
					...notes,
				].join(' ')
			}
			confirmLabel={action === 'delete' ? t('operate.processes.toolbar.delete') : t('operate.processes.toolbar.apply')}
			cancelLabel={t('operate.processes.toolbar.cancel')}
			isDestructive={action === 'delete'}
			isConfirmDisabled={isSubmitting || action === null || !selection.eligibility[action]}
			onOpenChange={onOpenChange}
			onCancel={onCancel}
			onConfirm={onConfirm}
		/>
	);
};

export {ProcessBulkActionDialog};
export type {ProcessBulkActionDialogProps};
