/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {InlineLoading, Link, ListItem, Stack, Tag, Tooltip} from '@carbon/react';
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {DrainingTag} from '#/operate/components/DrainingTag/DrainingTag';
import {DangerButton} from '#/operate/shared/OperationItem/DangerButton';
import {OperationItems} from '#/operate/shared/OperationItems/OperationItems';
import {DeleteButtonContainer} from './styled';
import {DeleteDefinitionModal} from '#/operate/shared/DeleteDefinitionModal/DeleteDefinitionModal';
import {UnorderedList} from '#/operate/shared/DeleteDefinitionModal/styled';
import {StructuredList} from '#/operate/shared/StructuredList/StructuredList';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';
import {drainingProcessDefinitionsQuery} from '#/operate/pages/Dashboard/InstancesByProcess/instancesByProcess.queries';
import {request, type RequestError} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getProcessDefinitionName} from '#/operate/pages/Processes/getProcessDefinitionName';
import {useDefinitionRunningInstancesCount} from './useDefinitionRunningInstancesCount';

type Props = {
	definition: ProcessDefinition;
};

const ProcessOperations: React.FC<Props> = ({definition}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
	const name = getProcessDefinitionName(definition);

	const {data: runningInstancesCount} = useDefinitionRunningInstancesCount(definition.processDefinitionKey);

	const {mutate: deleteDefinition, isPending: isOperationRunning} = useMutation<void, RequestError>({
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
			<DeleteButtonContainer>
				{isOperationRunning && <InlineLoading />}
				{isDraining ? (
					<DrainingTag
						label={t('operate.dashboard.draining')}
						description={t('operate.dashboard.drainingDescriptionVersion')}
						align="left-top"
					/>
				) : (
					<>
						{isDeleted && (
							<Tooltip label={t('operate.processes.definitionDeletion.deletedDescription')} align="left-top" autoAlign>
								<Tag type="red" size="md" tabIndex={0}>
									{t('operate.processes.definitionDeletion.deleted')}
								</Tag>
							</Tooltip>
						)}
						<OperationItems>
							<DangerButton
								type="DELETE"
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
			</DeleteButtonContainer>
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
					<Stack gap={6}>
						<UnorderedList nested>
							<ListItem>{t('operate.processes.definitionDeletion.warningFinishedInstances')}</ListItem>
							<ListItem>{t('operate.processes.definitionDeletion.warningReferencedInstances')}</ListItem>
							<ListItem>{t('operate.processes.definitionDeletion.warningUserTasks')}</ListItem>
						</UnorderedList>
						<Link
							href="https://docs.camunda.io/docs/components/operate/userguide/delete-resources/"
							target="_blank"
							rel="noreferrer"
						>
							{t('operate.processes.definitionDeletion.documentation')}
						</Link>
					</Stack>
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
