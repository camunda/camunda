/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Text, useIsMobile} from '@camunda/design-system';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {useNavigate} from '@tanstack/react-router';
import {ArrowLeft, CircleCheck, LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {AssigneeBadge} from '#/tasklist/modules/available-tasks/components/AssigneeBadge';

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
						<CircleCheck className="size-4 text-success-foreground-subtle" aria-hidden />
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
					<span className="flex items-center max-lg:order-2" data-testid="assignee">
						<AssigneeBadge currentUser={user} assignee={assignee} isShortFormat={false} />
					</span>
					<span className="flex shrink-0 items-center max-lg:order-1">{assignButton}</span>
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
	detailsButton?: React.ReactNode;
};

const TaskDetailsHeader: React.FC<Props> = ({
	taskName,
	processName,
	assignee,
	taskState,
	user,
	assignButton,
	detailsButton,
}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const isBelowMd = useIsMobile();

	return (
		<header
			className="flex w-full gap-4 px-4 pb-4 max-lg:flex-col max-lg:flex-nowrap max-lg:items-start max-lg:justify-start lg:flex-row lg:flex-wrap lg:items-center lg:justify-between"
			title={t('tasklist.taskDetailsHeader')}
		>
			{isBelowMd ? (
				<Button type="button" variant="ghost" size="sm" onClick={() => navigate({to: '/tasklist', search: true})}>
					<ArrowLeft aria-hidden />
					{t('tasklist.taskDetailsBackToListLabel')}
				</Button>
			) : null}
			<div className="flex min-w-40 flex-1 flex-col max-lg:w-full">
				<Text variant="label-md-strong" className="truncate text-neutral-foreground-strong">
					{taskName}
				</Text>
				<Text variant="helper" className="truncate text-neutral-foreground-subtle">
					{processName}
				</Text>
			</div>
			<div className="flex shrink-0 items-center gap-4 max-lg:ml-0 max-lg:w-full max-lg:justify-start lg:ml-auto lg:w-auto lg:justify-end">
				{detailsButton ? <span className="flex items-center max-lg:order-3">{detailsButton}</span> : null}
				<RightPanel taskState={taskState} assignee={assignee} user={user} assignButton={assignButton} />
			</div>
		</header>
	);
};

export {TaskDetailsHeader};
