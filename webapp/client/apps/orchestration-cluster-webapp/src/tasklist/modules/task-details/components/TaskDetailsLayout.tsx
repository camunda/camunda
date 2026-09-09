/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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
	TooltipTrigger,
	useMediaQuery,
} from '@camunda/design-system';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {useTranslation} from 'react-i18next';
import {Info, X} from 'lucide-react';
import {useHasRouteMatch} from '#/shared/useHasRouteMatch';
import {cn} from '#/shared/cn';
import {Aside} from './Aside';
import {TabListNav, type TabItem} from './TabListNav';
import {TaskDetailsHeader} from './TaskDetailsHeader';
import {useMemo} from 'react';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	assignButton: React.ReactNode;
	children: React.ReactNode;
};

const TaskDetailsLayout: React.FC<Props> = ({task, currentUser, assignButton, children}) => {
	const {t} = useTranslation();
	const hasRouteMatch = useHasRouteMatch();
	const isBelowXl = useMediaQuery('(width < 80rem)');
	const tabs = useMemo<TabItem[]>(
		() => [
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
		],
		[t, hasRouteMatch],
	);

	return (
		<Sheet>
			<div
				className={cn(
					'grid h-full w-full overflow-hidden',
					isBelowXl ? 'grid-cols-1' : 'grid-cols-[minmax(0,1fr)_19.5rem]',
				)}
				data-testid="details-info"
			>
				<section className="flex min-h-0 min-w-0 flex-col items-center gap-2 overflow-y-auto pt-4">
					<TaskDetailsHeader
						taskName={task.name ?? task.elementId}
						processName={task.processName ?? task.processDefinitionId}
						assignee={task.assignee ?? null}
						taskState={task.state}
						user={currentUser}
						assignButton={assignButton}
						detailsButton={
							isBelowXl ? (
								<Tooltip>
									<TooltipTrigger asChild>
										<SheetTrigger asChild>
											<Button
												type="button"
												variant="ghost"
												size="icon-sm"
												aria-label={t('tasklist.taskDetailsPanelTooltip')}
											>
												<Info aria-hidden />
											</Button>
										</SheetTrigger>
									</TooltipTrigger>
									<TooltipContent>{t('tasklist.taskDetailsPanelTooltip')}</TooltipContent>
								</Tooltip>
							) : null
						}
					/>
					<TabListNav label={t('tasklist.taskDetailsNavLabel')} items={tabs} userTaskKey={task.userTaskKey}>
						{children}
					</TabListNav>
				</section>
				{isBelowXl ? (
					<SheetContent side="right" showCloseButton={false}>
						<SheetHeader className="flex-row items-center justify-between">
							<SheetTitle>{t('tasklist.taskDetailsDetailsLabel')}</SheetTitle>
							<SheetClose asChild>
								<Button type="button" variant="ghost" size="icon-sm" aria-label={t('tasklist.optionsModalCloseButton')}>
									<X aria-hidden />
								</Button>
							</SheetClose>
						</SheetHeader>
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
							showTitle={!isBelowXl}
						/>
					</SheetContent>
				) : (
					<div className="min-h-0 border-l border-border pt-4">
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
							showTitle={!isBelowXl}
						/>
					</div>
				)}
			</div>
		</Sheet>
	);
};

export {TaskDetailsLayout};
