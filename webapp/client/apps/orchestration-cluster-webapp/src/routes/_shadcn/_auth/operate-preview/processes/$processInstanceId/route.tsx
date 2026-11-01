/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet, useLocation, useNavigate} from '@tanstack/react-router';
import {t} from 'i18next';
import {ProcessInstance} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstance';
import {
	getProcessInstanceTabFromPath,
	type ProcessInstanceTab,
} from '#/operate/pages/ProcessInstance/shadcn.components/processInstanceTabs';
import {processInstanceQuery} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {validateProcessInstanceRouteSearch} from '#/operate/pages/ProcessInstance/processInstanceSearch';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/processes/$processInstanceId')({
	validateSearch: validateProcessInstanceRouteSearch,
	loader: ({context: {queryClient}, params: {processInstanceId}}) =>
		queryClient.ensureQueryData(processInstanceQuery(processInstanceId)),
	head: ({loaderData, params: {processInstanceId}}) => ({
		meta: loaderData
			? [
					{
						title: t('operate.processInstance.pageTitle', {
							processInstanceId,
							name: getProcessDefinitionName(loaderData),
						}),
					},
				]
			: [],
	}),
	pendingComponent: () => <ProcessInstance activeTab="variables" />,
	component: function ProcessInstanceRoute() {
		const {processInstanceId} = Route.useParams();
		const navigate = useNavigate();
		const {pathname} = useLocation();

		const changeTab = (tab: ProcessInstanceTab) => {
			switch (tab) {
				case 'incidents':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/incidents',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
				case 'listeners':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/listeners',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
				case 'details':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/details',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
				case 'variables':
				default:
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/variables',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
			}
		};

		return (
			<ProcessInstance activeTab={getProcessInstanceTabFromPath(pathname)} onTabChange={(tab) => void changeTab(tab)}>
				<Outlet />
			</ProcessInstance>
		);
	},
});
