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
import {Trash2} from '@camunda/design-system/icons';
import type {CreateDecisionInstancesDeletionBatchOperationResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {BulkActionBar} from '#/operate/shared/BulkActionBar/shadcn.components/BulkActionBar';
import {BulkActionConfirmDialog} from '#/operate/shared/BulkActionBar/shadcn.components/BulkActionConfirmDialog';
import {formatOperationType} from '#/operate/shared/utils/formatOperationType';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';
import {buildInstanceKeyCriterion, type DecisionInstancesFilter} from '../decisionsFilter';

type Props = {
	selectedCount: number;
	includedIds: string[];
	excludedIds: string[];
	filter: DecisionInstancesFilter;
	onDeleted: () => void;
	onDiscard: () => void;
};

type KeyCriterion = {$eq?: string; $in?: string[]; $notIn?: string[]};

// Spreading alone would let a selection's $in/$notIn replace the filter's, so combine them instead.
function mergeKeyCriteria(base: KeyCriterion | undefined, selection: KeyCriterion): KeyCriterion {
	const merged = {...base, ...selection};
	if (base?.$in && selection.$in) {
		const baseIds = new Set(base.$in);
		merged.$in = selection.$in.filter((id) => baseIds.has(id));
	}
	if (base?.$notIn && selection.$notIn) {
		merged.$notIn = [...new Set([...base.$notIn, ...selection.$notIn])];
	}
	return merged;
}

const Toolbar: React.FC<Props> = ({selectedCount, includedIds, excludedIds, filter, onDeleted, onDiscard}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [showDeleteModal, setShowDeleteModal] = useState(false);
	const [isDeleting, setIsDeleting] = useState(false);

	if (selectedCount === 0) {
		return null;
	}

	const handleDelete = async () => {
		setShowDeleteModal(false);
		setIsDeleting(true);

		const criterion = buildInstanceKeyCriterion(includedIds, excludedIds);
		// Merge so exclusions don't drop an active instance-key filter ($in) and widen the deletion scope.
		const baseKey = filter.decisionEvaluationInstanceKey;
		const baseCriterion = typeof baseKey === 'string' ? {$eq: baseKey} : baseKey;
		const requestFilter = criterion
			? {...filter, decisionEvaluationInstanceKey: mergeKeyCriteria(baseCriterion, criterion)}
			: filter;

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

	return (
		<>
			<BulkActionBar
				selectedLabel={
					selectedCount === 1
						? t('operate.decisions.toolbar.itemSelected', {count: selectedCount})
						: t('operate.decisions.toolbar.itemsSelected', {count: selectedCount})
				}
				discardLabel={t('operate.decisions.toolbar.discard')}
				actions={[
					{
						key: 'delete',
						label: t('operate.decisions.toolbar.delete'),
						icon: <Trash2 aria-hidden />,
						loading: isDeleting,
						variant: 'destructive',
						onClick: () => setShowDeleteModal(true),
					},
				]}
				onDiscard={onDiscard}
			/>
			<BulkActionConfirmDialog
				open={showDeleteModal}
				title={t('operate.decisions.toolbar.deleteModalHeading')}
				description={t('operate.decisions.toolbar.deleteModalBody', {count: selectedCount})}
				confirmLabel={t('operate.decisions.toolbar.delete')}
				cancelLabel={t('operate.decisions.toolbar.deleteModalCancel')}
				isDestructive
				onOpenChange={setShowDeleteModal}
				onConfirm={() => void handleDelete()}
			/>
		</>
	);
};

export {Toolbar};
