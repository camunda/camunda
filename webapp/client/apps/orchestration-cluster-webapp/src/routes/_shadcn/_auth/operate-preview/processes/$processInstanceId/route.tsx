/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	createFileRoute,
	Outlet,
	useLocation,
	useNavigate,
	useRouter,
	type ErrorComponentProps,
} from '@tanstack/react-router';
import {useQueryClient, useSuspenseQuery} from '@tanstack/react-query';
import {t} from 'i18next';
import {ProcessInstance} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstance';
import {ProcessInstanceWithVisibleTabs} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceWithVisibleTabs';
import {
	getProcessInstanceTabFromPath,
	getVisibleProcessInstanceTabs,
	type ProcessInstanceTab,
} from '#/operate/pages/ProcessInstance/shadcn.components/processInstanceTabs';
import {ProcessInstanceBreadcrumb} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceBreadcrumb';
import {InstanceHistory} from '#/operate/pages/ProcessInstance/shadcn.components/InstanceHistory';
import {isWidthAboveBreakpoint, useMatchMedia} from '#/operate/shared/useMatchMedia';
import {InstanceDiagram} from '#/operate/pages/ProcessInstance/shadcn.components/InstanceDiagram';
import {ProcessInstanceError} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceError';
import {ProcessInstanceHeader} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceHeader';
import {ProcessInstanceHeaderSkeleton} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceHeaderSkeleton';
import {ProcessInstanceOperations} from '#/operate/pages/ProcessInstance/ProcessInstanceOperations/shadcn.components/ProcessInstanceOperations';
import {ProcessInstanceModificationFooter} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceModificationFooter';
import {ModificationLeaveDialog} from '#/operate/pages/ProcessInstance/shadcn.components/ModificationLeaveDialog';
import {ProcessInstanceHelperModal} from '#/operate/pages/ProcessInstance/shadcn.components/ProcessInstanceHelperModal';
import {useModificationController} from '#/operate/pages/ProcessInstance/useProcessInstanceModification';
import {useModificationLeaveBlocker} from '#/operate/pages/ProcessInstance/useModificationLeaveBlocker';
import {ProcessInstancePageProvider} from '#/operate/pages/ProcessInstance/ProcessInstancePageProvider';
import {useCallHierarchy} from '#/operate/shared/Operations/Operations.queries';
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
	pendingComponent: () => (
		<ProcessInstance
			activeTab="variables"
			visibleTabs={getVisibleProcessInstanceTabs({
				hasIncidentsTab: false,
				hasSelection: false,
				isProcessLevelWaiting: false,
			})}
			header={<ProcessInstanceHeaderSkeleton />}
		/>
	),
	errorComponent: function ProcessInstanceRouteError({error}: ErrorComponentProps) {
		const {processInstanceId} = Route.useParams();
		const router = useRouter();
		const queryClient = useQueryClient();

		if (error !== queryClient.getQueryState(processInstanceQuery(processInstanceId).queryKey)?.error) {
			throw error;
		}

		return (
			<ProcessInstanceError
				error={error}
				processInstanceId={processInstanceId}
				onRetry={() => void router.invalidate({filter: (match) => match.routeId === Route.id})}
			/>
		);
	},
	component: function ProcessInstanceRoute() {
		const {processInstanceId} = Route.useParams();
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data: processInstance} = useSuspenseQuery(processInstanceQuery(processInstanceId));
		const {data: hierarchy} = useCallHierarchy(processInstanceId, {enabled: true});
		const {pathname} = useLocation();
		const modification = useModificationController(processInstanceId);
		const {isModificationModeEnabled} = modification;
		const blocker = useModificationLeaveBlocker({processInstanceId, disabled: !isModificationModeEnabled});

		const withoutTabSearch = <T extends {sort?: string; operationType?: unknown; entityType?: unknown}>(prev: T) => ({
			...prev,
			sort: undefined,
			operationType: undefined,
			entityType: undefined,
		});
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
						search: withoutTabSearch,
						hash: true,
					});
				case 'input-mappings':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/input-mappings',
						params: {processInstanceId},
						search: withoutTabSearch,
						hash: true,
					});
				case 'output-mappings':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/output-mappings',
						params: {processInstanceId},
						search: withoutTabSearch,
						hash: true,
					});
				case 'operations-log':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/operations-log',
						params: {processInstanceId},
						search: withoutTabSearch,
						hash: true,
					});
				case 'listeners':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/listeners',
						params: {processInstanceId},
						search: withoutTabSearch,
						hash: true,
					});
				case 'details':
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/details',
						params: {processInstanceId},
						search: withoutTabSearch,
						hash: true,
					});
				case 'variables':
				default:
					return navigate({
						to: '/operate-preview/processes/$processInstanceId/variables',
						params: {processInstanceId},
						search: withoutTabSearch,
						hash: true,
					});
			}
		};

		const isDesktop = useMatchMedia(isWidthAboveBreakpoint('lg'));

		return (
			<ProcessInstancePageProvider
				processInstanceId={processInstanceId}
				processInstance={processInstance}
				search={search}
				modification={modification}
			>
				<ProcessInstanceWithVisibleTabs
					activeTab={getProcessInstanceTabFromPath(pathname)}
					isModificationModeEnabled={isModificationModeEnabled}
					isApplyingModifications={modification.state.status === 'applying-modifications'}
					footer={isModificationModeEnabled ? <ProcessInstanceModificationFooter /> : undefined}
					isDesktop={isDesktop}
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
				</ProcessInstanceWithVisibleTabs>
				<ModificationLeaveDialog
					open={blocker.status === 'blocked'}
					onStay={() => blocker.reset?.()}
					onLeave={() => blocker.proceed?.()}
				/>
				<ProcessInstanceHelperModal />
			</ProcessInstancePageProvider>
		);
	},
});
