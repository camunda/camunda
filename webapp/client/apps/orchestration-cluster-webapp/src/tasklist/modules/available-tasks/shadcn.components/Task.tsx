/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {Text} from '@camunda/design-system';
import {Link, useMatchRoute} from '@tanstack/react-router';
import {Bell, Calendar, CircleCheck, TriangleAlert} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {getNavLinkLabel} from '#/tasklist/modules/available-tasks/getNavLinkLabel';
import {getSecondaryDate} from '#/tasklist/modules/available-tasks/getSecondaryDate';
import {formatISODate, formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {AssigneeBadge} from './AssigneeBadge';
import {DateLabel} from './DateLabel';
import {PriorityLabel} from './PriorityLabel';

type Props = {
	userTaskKey: string;
	displayName: string;
	processDisplayName: string;
	businessId: string | null;
	assignee: string | null;
	creationDate: string;
	followUpDate: string | null;
	dueDate: string | null;
	completionDate: string | null;
	priority: number | null;
	currentUser: CurrentUser;
};

const Task = React.forwardRef<HTMLDivElement, Props>(
	(
		{
			userTaskKey,
			displayName,
			processDisplayName,
			businessId,
			assignee,
			creationDate: creationDateString,
			followUpDate: followUpDateString,
			dueDate: dueDateString,
			completionDate: completionDateString,
			priority,
			currentUser,
		},
		ref,
	) => {
		const {t} = useTranslation();
		const matchRoute = useMatchRoute();
		const isActive = matchRoute({to: '/shadcn/tasklist/$userTaskKey', params: {userTaskKey}, fuzzy: true}) !== false;
		const creationDate = formatISODateTime(creationDateString);
		const secondaryDate = getSecondaryDate({
			completionDate: formatISODate(completionDateString),
			dueDate: formatISODate(dueDateString),
			followUpDate: formatISODate(followUpDateString),
			sortBy: 'creation',
		});

		return (
			<article>
				<Link
					className={cn(
						'flex min-h-34 w-full flex-col gap-3 rounded-xl border border-border bg-neutral-background-subtle p-4 text-neutral-foreground-strong outline-none transition-colors',
						'hover:bg-neutral-background-medium focus-visible:ring-2 focus-visible:ring-ring',
						{'bg-neutral-background-strong hover:bg-neutral-background-strong': isActive},
					)}
					to="/shadcn/tasklist/$userTaskKey"
					search
					params={{userTaskKey}}
					aria-current={isActive ? 'page' : undefined}
					aria-label={getNavLinkLabel({
						displayName,
						assigneeId: assignee,
						currentUsername: currentUser.username,
					})}
				>
					<div className="flex h-full w-full flex-col gap-3" data-testid={`task-${userTaskKey}`} ref={ref}>
						<div className="flex min-h-5 flex-col justify-center">
							<Text variant="label-md-strong">{displayName}</Text>
							<Text variant="helper" className="text-neutral-foreground-subtle">
								{processDisplayName}
							</Text>
							{businessId === null ? null : (
								<Text variant="helper" className="text-neutral-foreground-subtle">
									{businessId}
								</Text>
							)}
						</div>

						<div className="flex min-h-5 items-center justify-between gap-2">
							<AssigneeBadge currentUser={currentUser} assignee={assignee} />
							{priority === null ? null : <PriorityLabel priority={priority} />}
						</div>

						<div data-testid="dates" className="flex min-h-5 items-end justify-between gap-2">
							{creationDate === null ? null : (
								<DateLabel
									date={creationDate}
									relativeLabel={t('tasklist.availableTasksCreatedRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksCreatedAbsoluteLabel')}
									icon={<Calendar className="size-4 shrink-0" aria-hidden />}
								/>
							)}
							{secondaryDate.followUpDate === undefined ? null : (
								<DateLabel
									date={secondaryDate.followUpDate}
									relativeLabel={t('tasklist.availableTasksFollowUpRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksFollowUpAbsoluteLabel')}
									icon={<Bell className="size-4 shrink-0 text-info-action-default" aria-hidden />}
									align="top-end"
								/>
							)}
							{secondaryDate.overDueDate === undefined ? null : (
								<DateLabel
									date={secondaryDate.overDueDate}
									relativeLabel={t('tasklist.availableTasksOverdueRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksOverdueAbsoluteLabel')}
									icon={<TriangleAlert className="size-4 shrink-0 text-danger-action-default" aria-hidden />}
									align="top-end"
								/>
							)}
							{secondaryDate.dueDate === undefined ? null : (
								<DateLabel
									date={secondaryDate.dueDate}
									relativeLabel={t('tasklist.availableTasksDueRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksDueAbsoluteLabel')}
									align="top-end"
								/>
							)}
							{secondaryDate.completionDate === undefined ? null : (
								<DateLabel
									date={secondaryDate.completionDate}
									relativeLabel={t('tasklist.availableTasksCompletedRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksCompletedAbsoluteLabel')}
									icon={<CircleCheck className="size-4 shrink-0 text-success-action-default" aria-hidden />}
									align="top-end"
								/>
							)}
						</div>
					</div>
				</Link>
			</article>
		);
	},
);

Task.displayName = 'Task';

export {Task};
