/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useRouter} from '@tanstack/react-router';
import {InlineLoading, Link, ListItem, Stack} from '@carbon/react';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {DangerButton} from '#/operate/shared/OperationItem/DangerButton';
import {OperationItems} from '#/operate/shared/OperationItems/OperationItems';
import {DeleteButtonContainer} from '#/operate/shared/DeleteDefinition/styled';
import {DeleteDefinitionModal} from '#/operate/shared/DeleteDefinitionModal/DeleteDefinitionModal';
import {UnorderedList} from '#/operate/shared/DeleteDefinitionModal/styled';
import {StructuredList} from '#/operate/shared/StructuredList/StructuredList';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {request, type RequestError} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {getDecisionDefinitionName} from '../getDecisionDefinitionName';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';

type Props = {
	definition: DecisionDefinition;
};

const DecisionOperations: React.FC<Props> = ({definition}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const router = useRouter();
	const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);

	const {mutate: deleteDefinition, isPending: isOperationRunning} = useMutation<
		void,
		RequestError,
		{tenantId: string | undefined}
	>({
		mutationFn: async () => {
			const {error} = await request(
				endpoints.deleteResource(definition.decisionRequirementsKey, {deleteHistory: true}),
			);

			if (error !== null) {
				throw error;
			}
		},
		onSuccess: (_, {tenantId}) => {
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('operate.decisions.definitionDeletion.successTitle'),
				isDismissable: true,
			});
			const {search} = router.state.location;
			if (
				search.decisionDefinitionId === definition.decisionDefinitionId &&
				search.decisionDefinitionVersion === definition.version &&
				search.tenantId === tenantId
			) {
				void router.navigate({
					to: '/operate/decisions',
					replace: true,
					search: (prev) => ({...prev, decisionDefinitionId: undefined, decisionDefinitionVersion: undefined}),
				});
			}
			void Promise.all([
				queryClient.invalidateQueries({queryKey: ['decisionDefinitions']}),
				queryClient.invalidateQueries({queryKey: ['queryDecisionDefinitions']}),
				queryClient.resetQueries({queryKey: ['decisionDefinitionVersions']}),
				queryClient.invalidateQueries({queryKey: ['decisionInstances']}),
			]);
		},
		onError: (error) => handleOperationError(error.response?.status),
	});

	return (
		<>
			<DeleteButtonContainer>
				{isOperationRunning && <InlineLoading />}
				<OperationItems>
					<DangerButton
						type="DELETE"
						title={t('operate.decisions.definitionDeletion.buttonTitle', {
							name: getDecisionDefinitionName(definition),
							version: definition.version,
						})}
						disabled={isOperationRunning}
						onClick={() => setIsDeleteModalVisible(true)}
					/>
				</OperationItems>
			</DeleteButtonContainer>
			<DeleteDefinitionModal
				isVisible={isDeleteModalVisible}
				title={t('operate.decisions.definitionDeletion.title')}
				description={t('operate.decisions.definitionDeletion.description')}
				confirmationText={t('operate.decisions.definitionDeletion.confirmation')}
				warningTitle={t('operate.decisions.definitionDeletion.warningTitle')}
				warningContent={
					<Stack gap={6}>
						<UnorderedList nested>
							<ListItem>{t('operate.decisions.definitionDeletion.warningDrd')}</ListItem>
							<ListItem>{t('operate.decisions.definitionDeletion.warningIncidents')}</ListItem>
						</UnorderedList>
						<Link href="https://docs.camunda.io/docs/components/operate/userguide/delete-resources/" target="_blank">
							{t('operate.decisions.definitionDeletion.documentation')}
						</Link>
					</Stack>
				}
				bodyContent={
					<StructuredList
						label={t('operate.decisions.definitionDeletion.details')}
						headerColumns={[{cellContent: t('operate.decisions.definitionDeletion.drdName')}]}
						rows={[
							{
								key: definition.decisionRequirementsKey,
								columns: [{cellContent: definition.decisionRequirementsName ?? definition.decisionRequirementsId}],
							},
						]}
					/>
				}
				onClose={() => setIsDeleteModalVisible(false)}
				onDelete={() => {
					setIsDeleteModalVisible(false);
					deleteDefinition({tenantId: router.state.location.search.tenantId});
				}}
			/>
		</>
	);
};

export {DecisionOperations};
