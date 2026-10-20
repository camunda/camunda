/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	AppHeader,
	AppSidebar,
	Avatar,
	CamundaLogo,
	NavBreadcrumbSwitcher,
	SidebarProvider,
	camundaAppIcons,
	type NavBreadcrumbDescriptor,
} from '@camunda/design-system';
import {Link, Outlet, useParams, useSearch} from '@tanstack/react-router';
import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useSidebarNavigation} from '#/shared/header/useSidebarNavigation.shadcn';
import {AsyncActionButton} from '#/tasklist/modules/task-details/components/AsyncActionButton';
import {TaskDetailsForm} from '#/tasklist/modules/task-details-form/components/TaskDetailsForm';
import {TaskDetailsVariables} from '#/tasklist/modules/task-details-variables/components/TaskDetailsVariables';
import {TaskDetailPage} from '#/tasklist/pages/TaskDetailPage';
import {TaskDetailsHistoryPage} from '#/tasklist/pages/TaskDetailsHistoryPage';
import {TaskDetailsProcessPage} from '#/tasklist/pages/TaskDetailsProcessPage';
import {
	PREVIEW_FORM_SCHEMA,
	PREVIEW_PROCESS_XML,
	PREVIEW_TASKS,
	PREVIEW_USER,
	getPreviewAuditLogs,
	getPreviewVariables,
} from './fixtures';

/*
 * Route components for the preview's in-memory router. They render the real Tasklist pages with
 * fixture data passed as props. Actions that would change data (assign, complete, start process)
 * are no-ops.
 */

const SIDEBAR_COLLAPSED_WIDTH = '3.5rem';
const SIDEBAR_EXPANDED_WIDTH = '12.25rem';

const noop = () => {};

/** Only the app crumb: the organization and cluster crumbs are SaaS context, not part of the theme. */
const BREADCRUMBS: NavBreadcrumbDescriptor[] = [
	{key: 'app', label: 'Tasklist', icon: camundaAppIcons.tasklist, linkProps: {to: '/tasklist'}},
];

function usePreviewTask() {
	const {userTaskKey} = useParams({from: '/_shadcn/_auth/tasklist/_tasks/$userTaskKey'});

	return PREVIEW_TASKS.find((task) => task.userTaskKey === userTaskKey);
}

const PreviewShell: React.FC = () => {
	const {t} = useTranslation();
	const {ariaLabel, items} = useSidebarNavigation(PREVIEW_USER);

	return (
		<SidebarProvider
			defaultExpanded={false}
			defaultWidth={SIDEBAR_EXPANDED_WIDTH}
			collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}
			isMobile={false}
		>
			<div className="h-full overflow-hidden bg-background text-neutral-foreground-strong">
				<AppHeader
					showSidebarTrigger={false}
					logo={
						<Link aria-label={t('loginLogoLabel')} className="flex items-center" to="/tasklist">
							<CamundaLogo />
						</Link>
					}
					breadcrumb={
						<NavBreadcrumbSwitcher
							items={BREADCRUMBS}
							activeItemKey="app"
							linkComponent={Link}
							aria-label={t('headerContextLabel')}
						/>
					}
					actions={<Avatar name={PREVIEW_USER.displayName} size="sm" />}
				/>
				<AppSidebar
					ariaLabel={ariaLabel}
					items={items}
					linkComponent={Link}
					resizable={false}
					expandedWidth={SIDEBAR_EXPANDED_WIDTH}
					collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}
				/>
				<div className="flex h-[calc(100%-3rem)]">
					<div className="w-(--app-sidebar-width) shrink-0 transition-[width] duration-150 ease-out" />
					<div className="min-w-0 flex-1 overflow-auto">
						<Outlet />
					</div>
				</div>
			</div>
		</SidebarProvider>
	);
};

const PreviewTaskDetail: React.FC = () => {
	const {t} = useTranslation();
	const task = usePreviewTask();

	if (task === undefined) {
		return null;
	}

	const isAssigned = task.assignee !== null;

	return (
		<TaskDetailPage
			task={task}
			currentUser={PREVIEW_USER}
			assignButton={
				<AsyncActionButton
					status="inactive"
					buttonProps={{variant: isAssigned ? 'secondary' : 'default', size: 'sm', type: 'button', onClick: noop}}
				>
					{isAssigned ? t('tasklist.taskDetailsUnassign') : t('tasklist.taskDetailsAssignToMe')}
				</AsyncActionButton>
			}
		>
			<Outlet />
		</TaskDetailPage>
	);
};

const PreviewTaskVariables: React.FC = () => {
	const task = usePreviewTask();
	const variables = useMemo(() => (task === undefined ? [] : getPreviewVariables(task)), [task]);

	if (task === undefined) {
		return null;
	}

	const isAssignedToUser = task.assignee === PREVIEW_USER.username && task.state === 'CREATED';

	if (task.formKey !== null) {
		return (
			<TaskDetailsForm
				key={task.userTaskKey}
				formSchema={PREVIEW_FORM_SCHEMA}
				variables={variables}
				completionStatus="inactive"
				isCompletionAllowed={isAssignedToUser}
				isHidden={task.state === 'COMPLETED'}
				onSubmit={noop}
			/>
		);
	}

	return (
		<TaskDetailsVariables
			key={task.userTaskKey}
			userTaskKey={task.userTaskKey}
			variables={variables}
			totalVariables={variables.length}
			isEditingAllowed={isAssignedToUser}
			isCompletionAllowed={isAssignedToUser}
			isCompleted={task.state === 'COMPLETED'}
			completionStatus="inactive"
			isCompletionHidden={task.state === 'COMPLETED'}
			hasNextPage={false}
			isFetchingNextPage={false}
			isNextPageError={false}
			onLoadNextPage={noop}
			onSubmit={noop}
		/>
	);
};

const PreviewTaskProcess: React.FC = () => {
	const task = usePreviewTask();

	if (task === undefined) {
		return null;
	}

	return <TaskDetailsProcessPage task={task} processXml={PREVIEW_PROCESS_XML} />;
};

const PreviewTaskHistory: React.FC = () => {
	const task = usePreviewTask();
	const search = useSearch({from: '/_shadcn/_auth/tasklist/_tasks/$userTaskKey/history'});
	const auditLogs = useMemo(() => (task === undefined ? [] : getPreviewAuditLogs(task)), [task]);

	if (task === undefined) {
		return null;
	}

	return (
		<TaskDetailsHistoryPage userTaskKey={task.userTaskKey} auditLogs={auditLogs} search={search} onScrollDown={noop} />
	);
};

export {PreviewShell, PreviewTaskDetail, PreviewTaskHistory, PreviewTaskProcess, PreviewTaskVariables};
