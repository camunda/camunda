/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet, useLocation, useNavigate} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {t} from 'i18next';
import {ProcessInstance} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstance';
import {
	getProcessInstanceTabFromPath,
	type ProcessInstanceTab,
} from '#/operate/pages/ProcessInstance/shadcn.components/processInstanceTabs';
import {ProcessInstanceBreadcrumb} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceBreadcrumb';
import {ProcessInstanceHeader} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceHeader';
import {ProcessInstanceHeaderSkeleton} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceHeaderSkeleton';
import {ProcessInstanceOperations} from '#/operate/pages/ProcessInstance/ProcessInstanceOperations/shadcn.components/ProcessInstanceOperations';
import {ProcessInstancePageProvider} from '#/operate/pages/ProcessInstance/ProcessInstancePageProvider';
import {useCallHierarchy} from '#/operate/shared/Operations/Operations.queries';
import {processInstanceQuery} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {validateProcessInstanceRouteSearch} from '#/operate/pages/ProcessInstance/processInstanceSearch';
import {loadOperateClientConfig} from '#/operate/shared/queries/loadOperateClientConfig';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {storeSessionState} from '#/shared/browser-storage/session-storage';

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/processes/$processInstanceId')({
	validateSearch: validateProcessInstanceRouteSearch,
	loader: async ({context: {queryClient}, params: {processInstanceId}}) => {
		storeSessionState('operateClientConfig', await loadOperateClientConfig(queryClient));
		return queryClient.ensureQueryData(processInstanceQuery(processInstanceId));
	},
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
	pendingComponent: () => <ProcessInstance activeTab="variables" header={<ProcessInstanceHeaderSkeleton />} />,
	component: function ProcessInstanceRoute() {
		const {processInstanceId} = Route.useParams();
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data: processInstance} = useSuspenseQuery(processInstanceQuery(processInstanceId));
		const {data: hierarchy} = useCallHierarchy(processInstanceId, {enabled: true});
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
			<ProcessInstancePageProvider
				processInstanceId={processInstanceId}
				processInstance={processInstance}
				search={search}
			>
				<ProcessInstance
					activeTab={getProcessInstanceTabFromPath(pathname)}
					onTabChange={(tab) => void changeTab(tab)}
					breadcrumb={
						hierarchy && hierarchy.length > 1 ? (
							<ProcessInstanceBreadcrumb callHierarchy={hierarchy.slice(0, -1)} processInstance={processInstance} />
						) : undefined
					}
					header={<ProcessInstanceHeader operations={<ProcessInstanceOperations key={processInstanceId} />} />}
				>
					<Outlet />
				</ProcessInstance>
			</ProcessInstancePageProvider>
		);
	},
});
