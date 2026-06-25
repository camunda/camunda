/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {useTranslation} from 'react-i18next';
import {Link, useParams} from '@tanstack/react-router';
import {Stack} from '@carbon/react';
import {Calendar, CheckmarkFilled, Warning, Notification} from '@carbon/react/icons';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {cn} from '#/shared/cn';
import {formatISODate, formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';
import {getSecondaryDate} from '#/tasklist/modules/available-tasks/getSecondaryDate';
import {getNavLinkLabel} from '#/tasklist/modules/available-tasks/getNavLinkLabel';
import {AssigneeTag} from './AssigneeTag';
import {DateLabel} from './DateLabel';
import {PriorityLabel} from './PriorityLabel';
import styles from './Task.module.scss';

type Props = {
	taskId: string;
	displayName: string;
	processDisplayName: string;
	assignee: string | null | undefined;
	creationDate: string;
	followUpDate: string | null | undefined;
	dueDate: string | null | undefined;
	completionDate: string | null | undefined;
	priority: number | null;
	currentUser: CurrentUser;
};

const Task = React.forwardRef<HTMLDivElement, Props>(
	(
		{
			taskId,
			displayName,
			processDisplayName,
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
		const {userTaskKey} = useParams({strict: false});
		const isActive = userTaskKey === taskId;

		const creationDate = formatISODateTime(creationDateString);
		const completionDate = formatISODate(completionDateString);
		const dueDate = formatISODate(dueDateString);
		const followUpDate = formatISODate(followUpDateString);
		const secondaryDate = getSecondaryDate({
			completionDate,
			dueDate,
			followUpDate,
			sortBy: 'creation',
		});

		return (
			<article className={cn(styles.container, {[styles.active!]: isActive})}>
				<Link
					className={styles.taskLink}
					to="/tasklist/$userTaskKey"
					params={{userTaskKey: taskId}}
					aria-label={getNavLinkLabel({
						displayName,
						assigneeId: assignee,
						currentUsername: currentUser.username,
					})}
				>
					<Stack className={styles.fullWidthAndHeight} data-testid={`task-${taskId}`} gap={3} ref={ref}>
						<div className={cn(styles.flex, styles.flexColumn)}>
							<span className={styles.name}>{displayName}</span>
							<span className={styles.label}>{processDisplayName}</span>
						</div>

						<div className={cn(styles.flex, styles.flexRow)}>
							<AssigneeTag currentUser={currentUser} assignee={assignee} />
							{priority === null ? null : <PriorityLabel priority={priority} />}
						</div>
						<div data-testid="dates" className={cn(styles.flex, styles.flexRow, styles.alignItemsEnd)}>
							{creationDate ? (
								<DateLabel
									date={creationDate}
									relativeLabel={t('tasklist.availableTasksCreatedRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksCreatedAbsoluteLabel')}
									icon={<Calendar className={styles.inlineIcon} />}
								/>
							) : null}
							{secondaryDate.followUpDate !== undefined ? (
								<DateLabel
									date={secondaryDate.followUpDate}
									relativeLabel={t('tasklist.availableTasksFollowUpRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksFollowUpAbsoluteLabel')}
									icon={<Notification className={styles.inlineIcon} color="blue" />}
									align="top-end"
								/>
							) : null}
							{secondaryDate.overDueDate !== undefined ? (
								<DateLabel
									date={secondaryDate.overDueDate}
									relativeLabel={t('tasklist.availableTasksOverdueRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksOverdueAbsoluteLabel')}
									icon={<Warning className={styles.inlineIcon} color="red" />}
									align="top-end"
								/>
							) : null}
							{secondaryDate.dueDate !== undefined ? (
								<DateLabel
									date={secondaryDate.dueDate}
									relativeLabel={t('tasklist.availableTasksDueRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksDueAbsoluteLabel')}
									align="top-end"
								/>
							) : null}
							{secondaryDate.completionDate !== undefined ? (
								<DateLabel
									date={secondaryDate.completionDate}
									relativeLabel={t('tasklist.availableTasksCompletedRelativeLabel')}
									absoluteLabel={t('tasklist.availableTasksCompletedAbsoluteLabel')}
									icon={<CheckmarkFilled className={styles.inlineIcon} color="green" />}
									align="top-end"
								/>
							) : null}
						</div>
					</Stack>
				</Link>
			</article>
		);
	},
);

Task.displayName = 'Task';

export {Task};
