/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useNavigate} from '@tanstack/react-router';
import type {CreateDecisionInstancesDeletionBatchOperationResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {BulkActionConfirmDialog} from '#/operate/shared/BulkActionBar/shadcn.components/BulkActionConfirmDialog';
import {formatOperationType} from '#/operate/shared/utils/formatOperationType';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';
import {applyInstanceSelection, type DecisionInstancesFilter} from '../decisionsFilter';

type Props = {
	selectedCount: number;
	includedIds: string[];
	excludedIds: string[];
	filter: DecisionInstancesFilter | undefined;
	onDeleted: () => void;
	onDiscard: () => void;
};

function useBulkDelete({selectedCount, includedIds, excludedIds, filter, onDeleted, onDiscard}: Props) {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [showDeleteModal, setShowDeleteModal] = useState(false);
	const [isDeleting, setIsDeleting] = useState(false);

	const handleDelete = async () => {
		setShowDeleteModal(false);
		if (filter === undefined) {
			return;
		}
		setIsDeleting(true);

		const requestFilter = applyInstanceSelection(filter, includedIds, excludedIds);

		const {response, error} = await request(
			endpoints.createDecisionInstancesDeletionBatchOperation({filter: requestFilter}),
		);

		setIsDeleting(false);

		if (error !== null) {
			handleOperationError(error.response?.status);
			return;
		}

		const {batchOperationKey, batchOperationType}: CreateDecisionInstancesDeletionBatchOperationResponseBody =
			await response.json();
		const operationTypeLabel = formatOperationType(batchOperationType);

		notificationsStore.displayNotification({
			kind: 'success',
			title: t('operate.decisions.toolbar.deleteSuccessTitle', {operationType: operationTypeLabel}),
			subtitle: t('operate.decisions.toolbar.deleteSuccessSubtitle'),
			isDismissable: true,
			isActionable: true,
			actionButtonLabel: t('operate.decisions.toolbar.deleteSuccessActionLabel'),
			onActionButtonClick: () => {
				void navigate({to: '/operate/batch-operations/$batchOperationKey', params: {batchOperationKey}});
			},
		});
		onDeleted();
	};

	const batchActions = [
		{
			id: 'delete',
			label: t('operate.decisions.toolbar.delete'),
			variant: 'destructive' as const,
			disabled: isDeleting,
			onClick: () => setShowDeleteModal(true),
		},
	];

	const selectionCountLabel =
		selectedCount === 1
			? t('operate.decisions.toolbar.itemSelected', {count: selectedCount})
			: t('operate.decisions.toolbar.itemsSelected', {count: selectedCount});

	const dialog = (
		<BulkActionConfirmDialog
			open={showDeleteModal}
			title={t('operate.decisions.toolbar.deleteModalHeading')}
			description={t('operate.decisions.toolbar.deleteModalBody', {count: selectedCount})}
			confirmLabel={t('operate.decisions.toolbar.delete')}
			cancelLabel={t('operate.decisions.toolbar.deleteModalCancel')}
			isDestructive
			onOpenChange={setShowDeleteModal}
			onCancel={onDiscard}
			onConfirm={() => void handleDelete()}
		/>
	);

	return {batchActions, selectionCountLabel, dialog};
}

export {useBulkDelete};
