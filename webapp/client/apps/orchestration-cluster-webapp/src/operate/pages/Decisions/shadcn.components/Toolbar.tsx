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
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	Button,
	buttonVariants,
	typographyVariants,
} from '@camunda/design-system';
import {Trash2} from '@camunda/design-system/icons';
import type {CreateDecisionInstancesDeletionBatchOperationResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
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
			? {...filter, decisionEvaluationInstanceKey: {...baseCriterion, ...criterion}}
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
			<div className="flex items-center justify-between gap-4 border-b border-border bg-neutral-background-subtle px-4 py-2">
				<span role="status" className={cn('text-neutral-foreground', typographyVariants({variant: 'label-md'}))}>
					{selectedCount === 1
						? t('operate.decisions.toolbar.itemSelected', {count: selectedCount})
						: t('operate.decisions.toolbar.itemsSelected', {count: selectedCount})}
				</span>
				<div className="flex items-center gap-2">
					<Button variant="ghost" size="sm" disabled={isDeleting} onClick={() => setShowDeleteModal(true)}>
						<Trash2 aria-hidden />
						{t('operate.decisions.toolbar.delete')}
					</Button>
					<Button variant="secondary" size="sm" onClick={onDiscard}>
						{t('operate.decisions.toolbar.discard')}
					</Button>
				</div>
			</div>

			<AlertDialog open={showDeleteModal} onOpenChange={setShowDeleteModal}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{t('operate.decisions.toolbar.deleteModalHeading')}</AlertDialogTitle>
					</AlertDialogHeader>
					<AlertDialogDescription>
						{t('operate.decisions.toolbar.deleteModalBody', {count: selectedCount})}
					</AlertDialogDescription>
					<AlertDialogFooter>
						<AlertDialogCancel onClick={onDiscard}>
							{t('operate.decisions.toolbar.deleteModalCancel')}
						</AlertDialogCancel>
						<AlertDialogAction
							className={buttonVariants({variant: 'destructive'})}
							onClick={(event) => {
								event.preventDefault();
								void handleDelete();
							}}
						>
							{t('operate.decisions.toolbar.delete')}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
		</>
	);
};

export {Toolbar};
