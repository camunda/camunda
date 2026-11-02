/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {
	Button,
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuTrigger,
	IconButton,
} from '@camunda/design-system';
import {EllipsisVertical, Pause, Play} from '@camunda/design-system/icons';
import type {BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.11';
import {useBatchOperationActions} from '../useBatchOperationActions';

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
		<div className="flex items-center gap-2">
			{allowedActions.includes('SUSPEND') && (
				<Button variant="secondary" onClick={() => suspend()} disabled={isSuspendPending}>
					<Pause aria-hidden data-icon="inline-start" />
					{t('operate.batchOperation.actions.suspend')}
				</Button>
			)}
			{allowedActions.includes('RESUME') && (
				<Button variant="secondary" onClick={() => resume()} disabled={isResumePending}>
					<Play aria-hidden data-icon="inline-start" />
					{t('operate.batchOperation.actions.resume')}
				</Button>
			)}
			{allowedActions.includes('CANCEL') && (
				<DropdownMenu>
					<DropdownMenuTrigger asChild>
						<IconButton
							variant="ghost"
							icon={EllipsisVertical}
							label={t('operate.batchOperation.actions.overflowMenuLabel')}
						/>
					</DropdownMenuTrigger>
					<DropdownMenuContent align="end">
						<DropdownMenuItem variant="destructive" onSelect={() => cancel()} disabled={isCancelPending}>
							{t('operate.batchOperation.actions.cancel')}
						</DropdownMenuItem>
					</DropdownMenuContent>
				</DropdownMenu>
			)}
		</div>
	);
};

export {BatchOperationActions};
