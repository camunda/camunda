/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Text} from '@camunda/design-system';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {CircleCheck, LoaderCircle} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {AssigneeBadge} from '#/tasklist/modules/available-tasks/shadcn.components/AssigneeBadge';

type RightPanelProps = {
	taskState: UserTask['state'];
	assignee: string | null;
	user: CurrentUser;
	assignButton: React.ReactNode;
};

const RightPanel: React.FC<RightPanelProps> = ({taskState, assignee, user, assignButton}) => {
	const {t} = useTranslation();

	switch (taskState) {
		case 'COMPLETED':
			return (
				<span
					className="flex items-center gap-4 text-xs text-neutral-foreground-subtle"
					data-testid="completion-label"
					title={t('tasklist.taskDetailsTaskCompletedBy')}
				>
					<span className="flex items-center gap-1">
						<CircleCheck className="size-4 text-success-action-default" aria-hidden />
						{assignee ? `${t('tasklist.taskDetailsTaskCompletedBy')} ` : t('tasklist.taskAssignmentStatusCompleted')}
					</span>
					{assignee ? (
						<span data-testid="assignee">
							<AssigneeBadge currentUser={user} assignee={assignee} />
						</span>
					) : null}
				</span>
			);
		case 'CREATED':
		case 'CANCELED':
		case 'FAILED':
			return (
				<>
					<span className="order-2 flex items-center lg:order-none" data-testid="assignee">
						<AssigneeBadge currentUser={user} assignee={assignee} isShortFormat={false} />
					</span>
					<span className="order-1 flex shrink-0 items-center lg:order-none">{assignButton}</span>
				</>
			);
		case 'UPDATING':
		case 'CANCELING':
			return (
				<>
					<span className="flex items-center gap-1.5 text-xs text-neutral-foreground-subtle">
						<LoaderCircle className="size-4 animate-spin" aria-hidden />
						{taskState === 'UPDATING'
							? t('tasklist.taskStateUpdatingMessage')
							: t('tasklist.taskStateCancelingMessage')}
					</span>
					<span className="flex items-center" data-testid="assignee">
						<AssigneeBadge currentUser={user} assignee={assignee} isShortFormat={false} />
					</span>
				</>
			);
		case 'COMPLETING':
			return (
				<span className="flex items-center" data-testid="assignee">
					<AssigneeBadge currentUser={user} assignee={assignee} isShortFormat={false} />
				</span>
			);
		case 'ASSIGNING':
			return <span className="flex shrink-0 items-center">{assignButton}</span>;
		case 'CREATING':
			return null;
	}
};

type Props = {
	taskName: string;
	processName: string;
	assignee: string | null;
	taskState: UserTask['state'];
	user: CurrentUser;
	assignButton: React.ReactNode;
};

const TaskDetailsHeader: React.FC<Props> = ({taskName, processName, assignee, taskState, user, assignButton}) => {
	const {t} = useTranslation();

	return (
		<header
			className="flex w-full flex-wrap items-center justify-between gap-4 px-4 pb-4"
			title={t('tasklist.taskDetailsHeader')}
		>
			<div className="flex min-w-40 flex-1 flex-col">
				<Text variant="label-md-strong" className="truncate text-neutral-foreground-strong">
					{taskName}
				</Text>
				<Text variant="helper" className="truncate text-neutral-foreground-subtle">
					{processName}
				</Text>
			</div>
			<div className="ml-auto flex shrink-0 items-center justify-end gap-4">
				<RightPanel taskState={taskState} assignee={assignee} user={user} assignButton={assignButton} />
			</div>
		</header>
	);
};

export {TaskDetailsHeader};
