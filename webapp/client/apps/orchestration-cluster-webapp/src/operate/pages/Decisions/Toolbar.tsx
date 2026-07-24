/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Modal, TableBatchAction, TableBatchActions, TableToolbar} from '@carbon/react';
import {TrashCan} from '@carbon/react/icons';
import type {CreateDecisionInstancesDeletionBatchOperationResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {tracking} from '#/shared/tracking';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {buildInstanceKeyCriterion, type DecisionInstancesFilter} from './decisionsFilter';

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
	const [showDeleteModal, setShowDeleteModal] = useState(false);
	const [isDeleting, setIsDeleting] = useState(false);

	if (selectedCount === 0) {
		return null;
	}

	const handleDelete = async () => {
		setShowDeleteModal(false);
		setIsDeleting(true);

		const criterion = buildInstanceKeyCriterion(includedIds, excludedIds);
		const requestFilter = criterion ? {...filter, decisionEvaluationInstanceKey: criterion} : filter;

		const {response, error} = await request(
			endpoints.createDecisionInstancesDeletionBatchOperation({filter: requestFilter}),
		);

		setIsDeleting(false);

		if (error !== null) {
			if (error.variant === 'failed-response' && error.response.status === 403) {
				notificationsStore.displayNotification({
					kind: 'warning',
					title: t('operate.decisions.toolbar.forbiddenTitle'),
					subtitle: t('operate.decisions.toolbar.forbiddenSubtitle'),
					isDismissable: true,
				});
				return;
			}
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.decisions.toolbar.deleteErrorTitle'),
				isDismissable: true,
			});
			return;
		}

		const {batchOperationKey, batchOperationType}: CreateDecisionInstancesDeletionBatchOperationResponseBody =
			await response.json();
		const operationTypeLabel = batchOperationType
			.split('_')
			.map((word) => word.charAt(0) + word.slice(1).toLowerCase())
			.join(' ');

		notificationsStore.displayNotification({
			kind: 'success',
			title: t('operate.decisions.toolbar.deleteSuccessTitle', {operationType: operationTypeLabel}),
			subtitle: t('operate.decisions.toolbar.deleteSuccessSubtitle'),
			isDismissable: true,
			isActionable: true,
			actionButtonLabel: t('operate.decisions.toolbar.deleteSuccessActionLabel'),
			onActionButtonClick: () => {
				// The batch operation detail route doesn't exist in the unified webapp yet, mirroring
				// the plain-href pattern BatchOperations already uses for the same not-yet-built route.
				window.location.assign(`/operate/batch-operations/${batchOperationKey}`);
			},
		});
		tracking.track({eventName: 'operate:batch-operation', operationType: 'DELETE_DECISION_INSTANCE'});
		onDeleted();
	};

	return (
		<>
			<TableToolbar size="sm">
				<TableBatchActions
					shouldShowBatchActions
					totalSelected={selectedCount}
					onCancel={onDiscard}
					translateWithId={(id) => {
						switch (id) {
							case 'carbon.table.batch.cancel':
								return t('operate.decisions.toolbar.discard');
							case 'carbon.table.batch.items.selected':
								return t('operate.decisions.toolbar.itemsSelected', {count: selectedCount});
							case 'carbon.table.batch.item.selected':
								return t('operate.decisions.toolbar.itemSelected', {count: selectedCount});
							case 'carbon.table.batch.selectAll':
								return t('operate.decisions.toolbar.selectAll');
							default:
								return id;
						}
					}}
				>
					<TableBatchAction renderIcon={TrashCan} disabled={isDeleting} onClick={() => setShowDeleteModal(true)}>
						{t('operate.decisions.toolbar.delete')}
					</TableBatchAction>
				</TableBatchActions>
			</TableToolbar>

			<Modal
				open={showDeleteModal}
				preventCloseOnClickOutside
				modalHeading={t('operate.decisions.toolbar.deleteModalHeading')}
				primaryButtonText={t('operate.decisions.toolbar.delete')}
				danger
				secondaryButtonText={t('operate.decisions.toolbar.deleteModalCancel')}
				onRequestSubmit={() => void handleDelete()}
				onRequestClose={() => setShowDeleteModal(false)}
				onSecondarySubmit={() => {
					setShowDeleteModal(false);
					onDiscard();
				}}
				size="md"
			>
				<p>{t('operate.decisions.toolbar.deleteModalBody', {count: selectedCount})}</p>
			</Modal>
		</>
	);
};

export {Toolbar};
