/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {OverflowMenu, OverflowMenuItem, Button} from '@carbon/react';
import {Pause, Play} from '@carbon/react/icons';
import type {BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.10';
import {useBatchOperationActions} from './useBatchOperationActions';
import {ActionsContainer} from './styled';

type Action = 'SUSPEND' | 'RESUME' | 'CANCEL';

const ACTIONS_BY_STATE: Record<BatchOperationState, Action[]> = {
	CREATED: ['SUSPEND', 'CANCEL'],
	ACTIVE: ['SUSPEND', 'CANCEL'],
	SUSPENDED: ['RESUME', 'CANCEL'],
	COMPLETED: [],
	PARTIALLY_COMPLETED: [],
	FAILED: [],
	CANCELED: [],
};

type Props = {
	batchOperationKey: string;
	batchOperationState: BatchOperationState;
};

const BatchOperationActions: React.FC<Props> = ({batchOperationKey, batchOperationState}) => {
	const {t} = useTranslation();
	const {suspend, resume, cancel, isSuspendPending, isResumePending, isCancelPending} =
		useBatchOperationActions(batchOperationKey);

	const allowedActions = ACTIONS_BY_STATE[batchOperationState];

	if (allowedActions.length === 0) {
		return null;
	}

	return (
		<ActionsContainer gap={2} orientation="horizontal">
			{allowedActions.includes('SUSPEND') && (
				<Button size="md" kind="tertiary" renderIcon={Pause} onClick={() => suspend()} disabled={isSuspendPending}>
					{t('operate.batchOperation.actions.suspend')}
				</Button>
			)}
			{allowedActions.includes('RESUME') && (
				<Button size="md" kind="tertiary" renderIcon={Play} onClick={() => resume()} disabled={isResumePending}>
					{t('operate.batchOperation.actions.resume')}
				</Button>
			)}
			{allowedActions.includes('CANCEL') && (
				<OverflowMenu
					size="md"
					iconDescription={t('operate.batchOperation.actions.overflowMenuLabel')}
					flipped
					align="bottom-end"
				>
					<OverflowMenuItem
						itemText={t('operate.batchOperation.actions.cancel')}
						isDelete
						onClick={() => cancel()}
						disabled={isCancelPending}
					/>
				</OverflowMenu>
			)}
		</ActionsContainer>
	);
};

export {BatchOperationActions};
