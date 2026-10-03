/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import {Button, Card, CardContent, CardDescription, CardHeader, CardTitle, EmptyState} from '@camunda/design-system';
import {ArrowRight, UserRound} from '@camunda/design-system/icons';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {DateLabel} from '#/tasklist/modules/available-tasks/components/DateLabel';
import {getUserTaskAssignment} from '#/tasklist/modules/case-details/getCaseWaits';
import {formatISODateTime} from '#/tasklist/modules/dates/formatDateRelative';

const OPEN_STATES = new Set<UserTask['state']>(['CREATED', 'ASSIGNING', 'UPDATING', 'COMPLETING', 'CANCELING']);

type Props = {
	userTasks: UserTask[];
	currentUsername: string;
};

const CaseTasksCard: React.FC<Props> = ({userTasks, currentUsername}) => {
	const {t} = useTranslation();
	const openTasks = useMemo(() => userTasks.filter(({state}) => OPEN_STATES.has(state)), [userTasks]);

	return (
		<Card>
			<CardHeader>
				<CardTitle>{t('tasklist.caseDetailsTasksTitle')}</CardTitle>
				<CardDescription>{t('tasklist.caseDetailsTasksDescription')}</CardDescription>
			</CardHeader>
			<CardContent>
				{openTasks.length === 0 ? (
					<EmptyState size="sm" heading={t('tasklist.caseDetailsNoOpenTasks')} />
				) : (
					<ul className="flex flex-col divide-y divide-border">
						{openTasks.map((userTask) => {
							const dueDate = formatISODateTime(userTask.dueDate);

							return (
								<li key={userTask.userTaskKey} className="flex items-center gap-3 py-3 first:pt-0 last:pb-0">
									<UserRound className="size-4 shrink-0 text-[color:var(--info-action-default)]" aria-hidden />
									<div className="flex min-w-0 flex-1 flex-col gap-0.5">
										<span className="truncate text-sm font-medium text-neutral-foreground-strong">
											{userTask.name || userTask.elementId}
										</span>
										<span className="truncate text-xs text-neutral-foreground-subtle">
											{getUserTaskAssignment(userTask, currentUsername)}
										</span>
									</div>
									{dueDate === null ? null : (
										<DateLabel
											date={dueDate}
											relativeLabel={t('tasklist.availableTasksDueRelativeLabel')}
											absoluteLabel={t('tasklist.availableTasksDueAbsoluteLabel')}
											align="top-end"
										/>
									)}
									<Button asChild variant="secondary" size="sm">
										<Link to="/tasklist/$userTaskKey" params={{userTaskKey: userTask.userTaskKey}}>
											{t('tasklist.caseDetailsOpenTask')}
											<ArrowRight aria-hidden />
										</Link>
									</Button>
								</li>
							);
						})}
					</ul>
				)}
			</CardContent>
		</Card>
	);
};

export {CaseTasksCard};
