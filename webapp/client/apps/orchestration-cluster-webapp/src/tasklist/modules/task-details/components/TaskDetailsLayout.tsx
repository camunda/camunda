/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {RegisteredRouter} from '@tanstack/react-router';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {Section} from '#/shared/design-system-compat';
import {useHasRouteMatch} from '#/shared/useHasRouteMatch';
import {
	Button,
	Sheet,
	SheetClose,
	SheetContent,
	SheetHeader,
	SheetTitle,
	SheetTrigger,
	Tooltip,
	TooltipContent,
	TooltipProvider,
	TooltipTrigger,
	useMediaQuery,
} from '@camunda/design-system';
import {Info, XIcon} from 'lucide-react';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {TaskDetailsHeader} from './TaskDetailsHeader';
import {TabListNav, type TabItem} from './TabListNav';
import {Aside} from './Aside';
import layoutStyles from './taskDetailsLayoutCommon.module.scss';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];
type TypeSafeTabItem = Omit<TabItem, 'to'> & {to: FileRouteTypes['to']};

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	assignButton: React.ReactNode;
	children: React.ReactNode;
};

const TaskDetailsLayout: React.FC<Props> = ({task, currentUser, assignButton, children}) => {
	const {t} = useTranslation();
	const hasRouteMatch = useHasRouteMatch();
	// DS-only: below `xl` (--breakpoint-xl, 80rem/1280px) the Aside details
	// panel moves into a Sheet instead of a permanent grid column — see
	// taskDetailsLayoutCommon.module.scss's .containerNoAsideColumnDS.
	const isBelowXl = useMediaQuery('(width < 80rem)');
	const showAsideInSheet = featureFlags.dsTasklistUI && isBelowXl;
	const tabs = [
		{
			key: 'task',
			title: t('tasklist.taskDetailsTaskTabLabel'),
			label: t('tasklist.taskDetailsShowTaskLabel'),
			selected: hasRouteMatch('/tasklist/$userTaskKey'),
			to: '/tasklist/$userTaskKey',
		},
		{
			key: 'process',
			title: t('tasklist.taskDetailsProcessTabLabel'),
			label: t('tasklist.taskDetailsShowBpmnProcessLabel'),
			selected: hasRouteMatch('/tasklist/$userTaskKey/process'),
			to: '/tasklist/$userTaskKey/process',
		},
		{
			key: 'history',
			title: t('tasklist.taskDetailsHistoryTabLabel'),
			label: t('tasklist.taskDetailsShowHistoryLabel'),
			selected: hasRouteMatch('/tasklist/$userTaskKey/history', '/tasklist/$userTaskKey/history/$auditLogKey'),
			to: '/tasklist/$userTaskKey/history',
		},
	] satisfies TypeSafeTabItem[];

	const aside = (
		<Aside
			creationDate={task.creationDate}
			completionDate={task.completionDate}
			dueDate={task.dueDate}
			followUpDate={task.followUpDate}
			priority={task.priority}
			candidateUsers={task.candidateUsers}
			candidateGroups={task.candidateGroups}
			tenantId={task.tenantId}
			businessId={task.businessId}
			user={currentUser}
			hideBorder={showAsideInSheet}
		/>
	);

	// DS-only: the "Task details" trigger used to sit in the tabs row, next to
	// the tab list. Moved into TaskDetailsHeader's right-hand container so it
	// can sit beside the assignee tag instead — see TaskDetailsHeader's
	// `detailsButton` prop for where it actually renders. <Sheet> itself has
	// to wrap this trigger AND <SheetContent> below from a common ancestor
	// (Radix context), even though they now render in different places; it
	// renders no DOM of its own, so wrapping the whole layout in it is free.
	const detailsButton = showAsideInSheet ? (
		<TooltipProvider>
			<Tooltip>
				<TooltipTrigger asChild>
					<SheetTrigger asChild>
						<Button variant="ghost" size="icon-sm" aria-label={t('tasklist.taskDetailsPanelTooltip')}>
							<Info aria-hidden />
						</Button>
					</SheetTrigger>
				</TooltipTrigger>
				<TooltipContent>{t('tasklist.taskDetailsPanelTooltip')}</TooltipContent>
			</Tooltip>
		</TooltipProvider>
	) : null;

	return (
		<div
			className={cn(layoutStyles.container, showAsideInSheet && layoutStyles.containerNoAsideColumnDS)}
			data-testid="details-info"
		>
			<Sheet>
				<Section className={layoutStyles.content} level={2}>
					<TurnOnNotificationPermission />
					<TaskDetailsHeader
						taskName={task.name ?? task.elementId}
						processName={task.processName ?? task.processDefinitionId}
						assignee={task.assignee ?? null}
						taskState={task.state}
						user={currentUser}
						assignButton={assignButton}
						detailsButton={detailsButton}
					/>
					{featureFlags.dsTasklistUI ? (
						<div className={layoutStyles.tabsRowDS}>
							<TabListNav label={t('tasklist.taskDetailsNavLabel')} items={tabs} />
						</div>
					) : (
						<TabListNav label={t('tasklist.taskDetailsNavLabel')} items={tabs} />
					)}
					{showAsideInSheet ? (
						<SheetContent side="right" showCloseButton={false}>
							<SheetHeader className="flex-row items-center justify-between">
								<SheetTitle>{t('tasklist.taskDetailsDetailsLabel')}</SheetTitle>
								<SheetClose asChild>
									<Button variant="ghost" size="icon-sm">
										<XIcon aria-hidden />
										<span className="sr-only">{t('tasklist.optionsModalCloseButton')}</span>
									</Button>
								</SheetClose>
							</SheetHeader>
							{aside}
						</SheetContent>
					) : null}
					{children}
				</Section>
				{showAsideInSheet ? null : aside}
			</Sheet>
		</div>
	);
};

export {TaskDetailsLayout};
