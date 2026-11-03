/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {createFileRoute, Outlet, useLocation, useNavigate} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {t} from 'i18next';
import {ProcessInstance} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstance';
import {
	PROCESS_INSTANCE_TABS,
	getHiddenTabRedirect,
	getProcessInstanceTabFromPath,
	getVisibleProcessInstanceTabs,
	type ProcessInstanceTab,
} from '#/operate/pages/ProcessInstance/shadcn.components/processInstanceTabs';
import {ProcessInstanceBreadcrumb} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceBreadcrumb';
import {InstanceHistory} from '#/operate/pages/ProcessInstance/shadcn.components/InstanceHistory';
import {isWidthAboveBreakpoint, useMatchMedia} from '#/operate/shared/useMatchMedia';
import {InstanceDiagram} from '#/operate/pages/ProcessInstance/shadcn.components/InstanceDiagram';
import {ProcessInstanceHeader} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceHeader';
import {ProcessInstanceHeaderSkeleton} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceHeaderSkeleton';
import {ProcessInstanceOperations} from '#/operate/pages/ProcessInstance/ProcessInstanceOperations/shadcn.components/ProcessInstanceOperations';
import {ProcessInstancePageProvider} from '#/operate/pages/ProcessInstance/ProcessInstancePageProvider';
import {useCallHierarchy} from '#/operate/shared/Operations/Operations.queries';
import {
	processInstanceQuery,
	useProcessInstanceWaitStateStatistics,
} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {validateProcessInstanceRouteSearch} from '#/operate/pages/ProcessInstance/processInstanceSearch';
import {loadOperateClientConfig} from '#/operate/shared/queries/loadOperateClientConfig';
import {getProcessDefinitionName, isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {hasProcessLevelWaitState} from '#/operate/shared/utils/waitStates';
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
	pendingComponent: () => (
		<ProcessInstance
			activeTab="variables"
			visibleTabs={PROCESS_INSTANCE_TABS}
			header={<ProcessInstanceHeaderSkeleton />}
		/>
	),
	component: function ProcessInstanceRoute() {
		const {processInstanceId} = Route.useParams();
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data: processInstance} = useSuspenseQuery(processInstanceQuery(processInstanceId));
		const {data: hierarchy} = useCallHierarchy(processInstanceId, {enabled: true});
		const {pathname} = useLocation();

		const changeTab = (tab: ProcessInstanceTab) => {
			switch (tab) {
				case 'history':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/history',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
				case 'incidents':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/incidents',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
				case 'input-mappings':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/input-mappings',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
				case 'output-mappings':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/output-mappings',
						params: {processInstanceId},
						search: true,
						hash: true,
					});
				case 'operations-log':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/operations-log',
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

		const hasSelection = Boolean(search.elementId || search.elementInstanceKey);
		const isDesktop = useMatchMedia(isWidthAboveBreakpoint('lg'));
		const visibleTabs = getVisibleProcessInstanceTabs(hasSelection, !isDesktop);
		const activeTab = getProcessInstanceTabFromPath(pathname);
		const {data: waitStateStatistics} = useProcessInstanceWaitStateStatistics(processInstance);
		const redirectTarget = getHiddenTabRedirect({
			activeTab,
			visibleTabs,
			hasSelection,
			isProcessLevelWaiting: hasProcessLevelWaitState(waitStateStatistics, processInstance.processDefinitionId),
			isWaitStateLoading: isInstanceRunning(processInstance) && waitStateStatistics === undefined,
		});

		useEffect(() => {
			if (redirectTarget !== null) {
				void navigate({
					to:
						redirectTarget === 'details'
							? '/operate-preview/processes/$processInstanceId/details'
							: '/operate-preview/processes/$processInstanceId/variables',
					params: {processInstanceId},
					search: true,
					hash: true,
					replace: true,
				});
			}
		}, [redirectTarget, navigate, processInstanceId]);

		return (
			<ProcessInstancePageProvider
				processInstanceId={processInstanceId}
				processInstance={processInstance}
				search={search}
			>
				<ProcessInstance
					activeTab={activeTab}
					visibleTabs={visibleTabs}
					onTabChange={(tab) => void changeTab(tab)}
					breadcrumb={
						hierarchy && hierarchy.length > 1 ? (
							<ProcessInstanceBreadcrumb callHierarchy={hierarchy.slice(0, -1)} processInstance={processInstance} />
						) : undefined
					}
					topPanel={<InstanceDiagram />}
					historyPanel={<InstanceHistory />}
					header={<ProcessInstanceHeader operations={<ProcessInstanceOperations key={processInstanceId} />} />}
				>
					<Outlet />
				</ProcessInstance>
			</ProcessInstancePageProvider>
		);
	},
});
