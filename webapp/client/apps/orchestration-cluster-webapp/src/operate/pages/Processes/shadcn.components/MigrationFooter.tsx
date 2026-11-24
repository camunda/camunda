/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OPERATE_BASE} from '#/shared/operateBase';
import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useMutation} from '@tanstack/react-query';
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
	Spinner,
} from '@camunda/design-system';
import {CircleAlert} from '@camunda/design-system/icons';
import type {
	CreateMigrationBatchOperationRequestBody,
	CreateMigrationBatchOperationResponseBody,
	ProcessDefinition,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {endpoints} from '#/shared/http/endpoints';
import {request, requestErrorSchema} from '#/shared/http/request';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {formatOperationType} from '#/operate/shared/utils/formatOperationType';
import type {MigrationScope} from '../getMigrationFilter';
import {MigrationConfirmationModal} from './MigrationConfirmationModal';

type MigrationStep = 'elementMapping' | 'summary';

type Props = {
	source: ProcessDefinition;
	target: ProcessDefinition | null;
	scope: MigrationScope;
	mapping: Record<string, string>;
	step: MigrationStep;
	onStepChange: (step: MigrationStep) => void;
	onExit: () => void;
};

const MigrationFooter: React.FC<Props> = ({source, target, scope, mapping, step, onStepChange, onExit}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [isExitOpen, setIsExitOpen] = useState(false);
	const [isConfirmationOpen, setIsConfirmationOpen] = useState(false);
	const hasElementMapping = Object.keys(mapping).length > 0;
	const migration = useMutation({
		mutationFn: async (
			body: CreateMigrationBatchOperationRequestBody,
		): Promise<CreateMigrationBatchOperationResponseBody> => {
			const {response, error} = await request(endpoints.createMigrationBatchOperation(body));
			if (error !== null) {
				throw error;
			}
			return response.json();
		},
		onSuccess: ({batchOperationKey, batchOperationType}) => {
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('operate.processes.toolbar.started', {operationType: formatOperationType(batchOperationType)}),
				subtitle: t('operate.processes.toolbar.progressSubtitle'),
				isDismissable: true,
				isActionable: true,
				actionButtonLabel: t('operate.processes.toolbar.details'),
				onActionButtonClick: () =>
					void navigate({
						to: `${OPERATE_BASE}/batch-operations/$batchOperationKey` as const,
						params: {batchOperationKey},
					}),
			});
		},
		onError: (error) => {
			const requestError = requestErrorSchema.safeParse(error);
			const isForbidden = requestError.success && requestError.data.response?.status === 403;
			notificationsStore.displayNotification({
				kind: isForbidden ? 'warning' : 'error',
				title: t(isForbidden ? 'operate.processes.toolbar.forbidden' : 'operate.processes.toolbar.failed'),
				subtitle: isForbidden ? t('operate.processes.toolbar.contactAdministrator') : undefined,
				isDismissable: true,
				autoRemove: false,
			});
		},
	});
	const {status, reset} = migration;
	const isPending = status === 'pending';

	useEffect(() => {
		if (status !== 'error') {
			return undefined;
		}
		const timeoutId = setTimeout(reset, 2000);
		return () => clearTimeout(timeoutId);
	}, [status, reset]);

	const submit = () => {
		if (target === null || !hasElementMapping) {
			return;
		}
		setIsConfirmationOpen(false);
		migration.mutate(
			{
				filter: scope.filter,
				migrationPlan: {
					targetProcessDefinitionKey: target.processDefinitionKey,
					mappingInstructions: Object.entries(mapping).map(([sourceElementId, targetElementId]) => ({
						sourceElementId,
						targetElementId,
					})),
				},
			},
			{
				onSuccess: () => {
					onExit();
					void navigate({
						to: `${OPERATE_BASE}/processes` as const,
						search: {
							active: true,
							suspended: true,
							incidents: true,
							process: target.processDefinitionId,
							version: target.version,
							tenantId: getClientConfig().deployment.isMultiTenancyEnabled ? target.tenantId : undefined,
						},
						ignoreBlocker: true,
					});
				},
			},
		);
	};

	return (
		<div className="flex w-full items-center justify-end gap-4 border-t bg-background px-4 py-2">
			<Button variant="secondary" size="sm" disabled={isPending} onClick={() => setIsExitOpen(true)}>
				{t('operate.processes.migration.exit')}
			</Button>
			{step === 'elementMapping' && (
				<Button
					size="sm"
					onClick={() => onStepChange('summary')}
					disabled={!hasElementMapping}
					title={hasElementMapping ? undefined : t('operate.processes.migration.mappingRequired')}
				>
					{t('operate.processes.migration.next')}
				</Button>
			)}
			{step === 'summary' && (
				<>
					<Button variant="secondary" size="sm" disabled={isPending} onClick={() => onStepChange('elementMapping')}>
						{t('operate.processes.migration.back')}
					</Button>
					{status === 'idle' ? (
						<Button size="sm" onClick={() => setIsConfirmationOpen(true)}>
							{t('operate.processes.migration.confirm')}
						</Button>
					) : status === 'pending' ? (
						<Spinner label={t('operate.processes.migration.migrating')} />
					) : status === 'error' ? (
						<span role="status" className="flex items-center gap-2 text-sm whitespace-nowrap">
							<CircleAlert aria-hidden className="size-4" />
							{t('operate.processes.migration.failed')}
						</span>
					) : null}
				</>
			)}
			<AlertDialog open={isExitOpen} onOpenChange={setIsExitOpen}>
				<AlertDialogContent>
					<AlertDialogHeader>
						<AlertDialogTitle>{t('operate.processes.migration.exitTitle')}</AlertDialogTitle>
					</AlertDialogHeader>
					<AlertDialogDescription>
						{t('operate.processes.migration.exitDiscard')} {t('operate.processes.migration.exitProceed')}
					</AlertDialogDescription>
					<AlertDialogFooter>
						<AlertDialogCancel>{t('operate.processes.toolbar.cancel')}</AlertDialogCancel>
						<AlertDialogAction
							className={buttonVariants({variant: 'destructive'})}
							onClick={() => {
								setIsExitOpen(false);
								onExit();
							}}
						>
							{t('operate.processes.migration.exitConfirm')}
						</AlertDialogAction>
					</AlertDialogFooter>
				</AlertDialogContent>
			</AlertDialog>
			{isConfirmationOpen && target !== null && (
				<MigrationConfirmationModal
					source={source}
					target={target}
					scope={scope}
					hasElementMapping={hasElementMapping}
					onClose={() => setIsConfirmationOpen(false)}
					onSubmit={submit}
				/>
			)}
		</div>
	);
};

export {MigrationFooter};
export type {MigrationStep};
