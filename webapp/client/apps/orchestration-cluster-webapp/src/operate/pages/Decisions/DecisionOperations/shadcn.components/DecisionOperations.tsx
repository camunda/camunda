/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useIsMutating, useMutation, useQueryClient} from '@tanstack/react-query';
import {useRouter} from '@tanstack/react-router';
import {Link} from '@camunda/design-system';
import {LoaderCircle} from '@camunda/design-system/icons';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {DangerButton} from '#/operate/shared/OperationItem/shadcn.components/DangerButton';
import {OperationItems} from '#/operate/shared/OperationItems/shadcn.components/OperationItems';
import {DeleteDefinitionModal} from '#/operate/shared/DeleteDefinitionModal/shadcn.components/DeleteDefinitionModal';
import {StructuredList} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {request, type RequestError} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {getDecisionDefinitionName} from '../../getDecisionDefinitionName';
import {handleOperationError} from '#/operate/shared/utils/handleOperationError';

type Props = {
	definition: DecisionDefinition;
};

const DecisionOperations: React.FC<Props> = ({definition}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const router = useRouter();
	const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);

	const mutationKey = ['deleteDecisionRequirements', definition.decisionRequirementsKey];
	const isOperationRunning = useIsMutating({mutationKey}) > 0;

	const {mutate: deleteDefinition} = useMutation<
		void,
		RequestError,
		{decisionDefinitionId: string; version: number; tenantId: string | undefined}
	>({
		mutationKey,
		mutationFn: async () => {
			const {error} = await request(
				endpoints.deleteResource(definition.decisionRequirementsKey, {deleteHistory: true}),
			);

			if (error !== null) {
				throw error;
			}
		},
		onSuccess: (_, {decisionDefinitionId, version, tenantId}) => {
			notificationsStore.displayNotification({
				kind: 'success',
				title: t('operate.decisions.definitionDeletion.successTitle'),
				isDismissable: true,
			});
			const {search} = router.state.location;
			if (
				search.decisionDefinitionId === decisionDefinitionId &&
				search.decisionDefinitionVersion === version &&
				search.tenantId === tenantId
			) {
				void router.navigate({
					to: '.',
					replace: true,
					search: (prev) => ({
						...prev,
						evaluated: prev.evaluated ?? false,
						failed: prev.failed ?? false,
						decisionDefinitionId: undefined,
						decisionDefinitionVersion: undefined,
					}),
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
			<div className="ml-auto flex items-center">
				{isOperationRunning && (
					<span role="status" aria-live="polite" className="flex items-center">
						<LoaderCircle aria-hidden="true" className="size-4 animate-spin" />
						<span className="sr-only">{t('operate.decisions.definitionDeletion.loadingMessage')}</span>
					</span>
				)}
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
			</div>
			<DeleteDefinitionModal
				isVisible={isDeleteModalVisible}
				title={t('operate.decisions.definitionDeletion.title')}
				description={t('operate.decisions.definitionDeletion.description')}
				confirmationText={t('operate.decisions.definitionDeletion.confirmation')}
				warningTitle={t('operate.decisions.definitionDeletion.warningTitle')}
				warningContent={
					<div className="space-y-6">
						<ul className="list-disc pl-5">
							<li>{t('operate.decisions.definitionDeletion.warningDrd')}</li>
							<li>{t('operate.decisions.definitionDeletion.warningIncidents')}</li>
						</ul>
						<Link
							href="https://docs.camunda.io/docs/components/operate/userguide/delete-resources/"
							target="_blank"
							rel="noreferrer"
							className="text-neutral-foreground-strong underline"
						>
							{t('operate.decisions.definitionDeletion.documentation')}
						</Link>
					</div>
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
					deleteDefinition({
						decisionDefinitionId: definition.decisionDefinitionId,
						version: definition.version,
						tenantId: router.state.location.search.tenantId,
					});
				}}
			/>
		</>
	);
};

export {DecisionOperations};
