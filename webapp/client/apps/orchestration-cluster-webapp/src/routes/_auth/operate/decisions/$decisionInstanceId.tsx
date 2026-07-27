/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {t} from 'i18next';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {DecisionInstance, DecisionInstanceShell} from '#/operate/pages/DecisionInstance/DecisionInstance';
import {getHeaderColumns} from '#/operate/pages/DecisionInstance/headerColumns';
import {decisionInstanceQuery} from '#/operate/pages/DecisionInstance/decisionInstance.queries';
import {InstanceHeaderSkeleton} from '#/operate/shared/InstanceHeader/InstanceHeaderSkeleton';

export const Route = createFileRoute('/_auth/operate/decisions/$decisionInstanceId')({
	loader: async ({context: {queryClient}, params: {decisionInstanceId}}) => {
		try {
			return await queryClient.ensureQueryData(decisionInstanceQuery(decisionInstanceId));
		} catch {
			// 403/404/network errors are handled by the page itself (forbidden state, redirect+notify);
			// the loader only feeds `head()` below, so a missing title is the only consequence here.
			return undefined;
		}
	},
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
	// Mirrors Header's own pending state (same `getHeaderColumns` + `InstanceHeaderSkeleton`) so
	// navigation shows the same loading row legacy did, instead of a blank page while the loader awaits.
	pendingComponent: () => (
		<DecisionInstanceShell
			header={
				<InstanceHeaderSkeleton
					headerColumns={getHeaderColumns(t, {
						isMultiTenancyEnabled: getClientConfig().deployment.isMultiTenancyEnabled,
					})}
				/>
			}
		/>
	),
	component: function DecisionInstanceRoute() {
		const {decisionInstanceId} = Route.useParams();
		return <DecisionInstance decisionInstanceId={decisionInstanceId} />;
	},
});
