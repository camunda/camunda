/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useIsMutating, useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {Badge, Link, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {LoaderCircle} from '@camunda/design-system/icons';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {DrainingTag} from '#/operate/components/DrainingTag/shadcn.components/DrainingTag';
import {DangerButton} from '#/operate/shared/OperationItem/shadcn.components/DangerButton';
import {OperationItems} from '#/operate/shared/OperationItems/shadcn.components/OperationItems';
import {DeleteDefinitionModal} from '#/operate/shared/DeleteDefinitionModal/shadcn.components/DeleteDefinitionModal';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';
import {drainingProcessDefinitionsQuery} from '#/operate/pages/Dashboard/InstancesByProcess/instancesByProcess.queries';
import {request, type RequestError} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getProcessDefinitionName} from '../getProcessDefinitionName';
import {useDefinitionRunningInstancesCount} from '../ProcessOperations/useDefinitionRunningInstancesCount';

type Props = {
	definition: ProcessDefinition;
};

const ProcessOperations: React.FC<Props> = ({definition}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
	const name = getProcessDefinitionName(definition);

	const {data: runningInstancesCount} = useDefinitionRunningInstancesCount(definition.processDefinitionKey);

	const mutationKey = ['deleteProcessDefinition', definition.processDefinitionKey];
	const isOperationRunning = useIsMutating({mutationKey}) > 0;

	const {mutate: deleteDefinition} = useMutation<void, RequestError>({
		mutationKey,
		mutationFn: async () => {
			const {error} = await request(endpoints.deleteResource(definition.processDefinitionKey, {deleteHistory: true}));

			if (error !== null) {
				throw error;
			}
		},
		onSuccess: () => {
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('operate.processes.definitionDeletion.successTitle'),
				isDismissable: true,
			});
			void Promise.all([
				queryClient.invalidateQueries({queryKey: ['queryProcessDefinitions']}),
				queryClient.invalidateQueries({queryKey: ['operationsLogDefinitions']}),
				queryClient.invalidateQueries({queryKey: ['drainingProcessDefinitions']}),
			]);
		},
		onError: (error) => handleOperationError(error.response?.status),
	});

	const {data: draining} = useQuery(drainingProcessDefinitionsQuery());
	const isDeleted = definition.state === 'DELETED';
	const isDraining =
		!isDeleted && (definition.state === 'DRAINING' || !!draining?.byKey.has(definition.processDefinitionKey));
	const hasRunningInstances = (runningInstancesCount ?? 0) > 0;

	return (
		<>
			<div className="ml-auto flex shrink-0 items-center gap-2 pl-4">
				{isOperationRunning && (
					<span role="status" aria-live="polite" className="flex items-center">
						<LoaderCircle aria-hidden="true" className="size-4 animate-spin" />
						<span className="sr-only">{t('operate.processes.definitionDeletion.loadingMessage')}</span>
					</span>
				)}
				{isDraining ? (
					<DrainingTag
						label={t('operate.dashboard.draining')}
						description={t('operate.dashboard.drainingDescriptionVersion')}
						align="left-top"
					/>
				) : (
					<>
						{isDeleted && (
							<Tooltip>
								<TooltipTrigger asChild>
									<Badge variant="danger" tabIndex={0}>
										{t('operate.processes.definitionDeletion.deleted')}
									</Badge>
								</TooltipTrigger>
								<TooltipContent side="left" align="start">
									{t('operate.processes.definitionDeletion.deletedDescription')}
								</TooltipContent>
							</Tooltip>
						)}
						<OperationItems>
							<DangerButton
								type="DELETE"
								isIconOnly
								title={
									hasRunningInstances
										? t('operate.processes.definitionDeletion.runningInstances')
										: t(
												isDeleted
													? 'operate.processes.definitionDeletion.historyButtonTitle'
													: 'operate.processes.definitionDeletion.buttonTitle',
												{name, version: definition.version},
											)
								}
								disabled={isOperationRunning || hasRunningInstances}
								onClick={() => setIsDeleteModalVisible(true)}
							/>
						</OperationItems>
					</>
				)}
			</div>
			<DeleteDefinitionModal
				key={`${definition.processDefinitionKey}-${isDeleted ? 'history' : 'definition'}`}
				isVisible={isDeleteModalVisible}
				title={t('operate.processes.definitionDeletion.title')}
				description={t(
					isDeleted
						? 'operate.processes.definitionDeletion.historyDescription'
						: 'operate.processes.definitionDeletion.description',
				)}
				confirmationText={t(
					isDeleted
						? 'operate.processes.definitionDeletion.historyConfirmation'
						: 'operate.processes.definitionDeletion.confirmation',
				)}
				warningTitle={t(
					isDeleted
						? 'operate.processes.definitionDeletion.historyWarningTitle'
						: 'operate.processes.definitionDeletion.warningTitle',
				)}
				warningContent={
					<div className="space-y-6">
						<ul className="list-disc pl-5">
							<li>{t('operate.processes.definitionDeletion.warningFinishedInstances')}</li>
							<li>{t('operate.processes.definitionDeletion.warningReferencedInstances')}</li>
							<li>{t('operate.processes.definitionDeletion.warningUserTasks')}</li>
						</ul>
						<Link
							href="https://docs.camunda.io/docs/components/operate/userguide/delete-resources/"
							target="_blank"
							rel="noreferrer"
							className="text-neutral-foreground-strong underline"
						>
							{t('operate.processes.definitionDeletion.documentation')}
						</Link>
					</div>
				}
				bodyContent={
					<StructuredList
						label={t('operate.processes.definitionDeletion.details')}
						headerColumns={[{cellContent: t('operate.processes.definitionDeletion.definition')}]}
						rows={[
							{
								key: definition.processDefinitionKey,
								columns: [
									{
										cellContent: t('operate.processes.definitionDeletion.definitionVersion', {
											name,
											version: definition.version,
										}),
									},
								],
							},
						]}
					/>
				}
				onClose={() => setIsDeleteModalVisible(false)}
				onDelete={() => {
					setIsDeleteModalVisible(false);
					deleteDefinition();
				}}
			/>
		</>
	);
};

export {ProcessOperations};
