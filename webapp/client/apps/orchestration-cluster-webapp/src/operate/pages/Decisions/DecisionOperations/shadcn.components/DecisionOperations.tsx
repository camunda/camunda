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
import {DataTable, Link} from '@camunda/design-system';
import {LoaderCircle} from '@camunda/design-system/icons';
import type {DecisionDefinition, DeleteResourceResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {DangerButton} from '#/operate/shared/OperationItem/shadcn.components/DangerButton';
import {OperationItems} from '#/operate/shared/OperationItems/shadcn.components/OperationItems';
import {DeleteDefinitionModal} from '#/operate/shared/DeleteDefinitionModal/shadcn.components/DeleteDefinitionModal';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {request, type RequestError} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {OPERATE_BASE} from '#/shared/operateBase';
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
	const drdName = definition.decisionRequirementsName || definition.decisionRequirementsId;

	const {mutate: deleteDefinition} = useMutation<
		string | undefined,
		RequestError,
		{decisionDefinitionId: string; version: number; tenantId: string | undefined}
	>({
		mutationKey,
		mutationFn: async () => {
			const {response, error} = await request(
				endpoints.deleteResource(definition.decisionRequirementsKey, {deleteHistory: true}),
			);

			if (error !== null) {
				throw error;
			}

			const body = (await response.json().catch(() => null)) as DeleteResourceResponseBody | null;
			return body?.batchOperation?.batchOperationKey;
		},
		onSuccess: (batchOperationKey, {decisionDefinitionId, version, tenantId}) => {
			notificationsStore.displayNotification(
				batchOperationKey === undefined
					? {
							kind: 'success',
							title: t('operate.decisions.definitionDeletion.successTitle'),
							isDismissable: true,
						}
					: {
							kind: 'success',
							title: t('operate.decisions.definitionDeletion.successTitle'),
							isDismissable: true,
							isActionable: true,
							actionButtonLabel: t('operate.decisions.ds.toolbar.deleteSuccessActionLabel'),
							onActionButtonClick: () => {
								void router.navigate({
									to: `${OPERATE_BASE}/batch-operations/$batchOperationKey` as const,
									params: {batchOperationKey},
								});
							},
						},
			);
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
						label={t('operate.decisions.ds.definitionDeletion.buttonLabel')}
						title={t('operate.decisions.ds.definitionDeletion.buttonTitle', {
							name: drdName,
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
					<DataTable
						aria-label={t('operate.decisions.ds.definitionDeletion.details')}
						columns={[{id: 'drdName', header: t('operate.decisions.definitionDeletion.drdName'), accessorKey: 'name'}]}
						data={[{name: definition.decisionRequirementsName || definition.decisionRequirementsId}]}
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
