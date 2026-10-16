/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, useRouter, type ErrorComponentProps} from '@tanstack/react-router';
import {useQueryClient} from '@tanstack/react-query';
import {t} from 'i18next';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {
	DecisionInstance,
	DecisionInstanceShell,
} from '#/operate/pages/DecisionInstance/shadcn.components/DecisionInstance';
import {DecisionInstanceError} from '#/operate/pages/DecisionInstance/shadcn.components/DecisionInstanceError';
import {getHeaderColumns} from '#/operate/pages/DecisionInstance/headerColumns';
import {decisionInstanceQuery} from '#/operate/pages/DecisionInstance/decisionInstance.queries';
import {InstanceHeaderSkeleton} from '#/operate/shared/InstanceHeader/shadcn.components/InstanceHeaderSkeleton';

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/decisions/$decisionInstanceId')({
	loader: ({context: {queryClient}, params: {decisionInstanceId}}) =>
		queryClient.ensureQueryData(decisionInstanceQuery(decisionInstanceId)),
	head: ({loaderData, params: {decisionInstanceId}}) => ({
		meta: loaderData
			? [
					{
						title: t('operate.decisionInstance.pageTitle', {
							decisionInstanceId,
							name: loaderData.decisionDefinitionName,
						}),
					},
				]
			: [],
	}),
	pendingComponent: () => (
		<DecisionInstanceShell
			isPending
			header={
				<InstanceHeaderSkeleton
					headerColumns={getHeaderColumns(t, {
						isMultiTenancyEnabled: getClientConfig().deployment.isMultiTenancyEnabled,
					})}
				/>
			}
		/>
	),
	errorComponent: function DecisionInstanceRouteError({error}: ErrorComponentProps) {
		const {decisionInstanceId} = Route.useParams();
		const router = useRouter();
		const queryClient = useQueryClient();

		if (error !== queryClient.getQueryState(decisionInstanceQuery(decisionInstanceId).queryKey)?.error) {
			throw error;
		}

		return (
			<DecisionInstanceError
				error={error}
				decisionInstanceId={decisionInstanceId}
				onRetry={() => void router.invalidate({filter: (match) => match.routeId === Route.id})}
			/>
		);
	},
	component: function DecisionInstanceRoute() {
		const {decisionInstanceId} = Route.useParams();

		return <DecisionInstance decisionInstanceId={decisionInstanceId} />;
	},
});
