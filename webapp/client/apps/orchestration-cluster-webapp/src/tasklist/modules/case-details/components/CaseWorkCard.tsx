/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {
	Button,
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
	EmptyState,
	Heading,
	Spinner,
	Text,
} from '@camunda/design-system';
import {ArrowRight, Check, CircleCheck, CirclePlay, LoaderCircle, UserRound} from '@camunda/design-system/icons';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';
import type {ActivityStatus, StageActivity, StageWork} from '#/tasklist/modules/case-details/getStageWork';

function useAssignmentLabel() {
	const {t} = useTranslation();

	return (userTask: UserTask, currentUsername: string): string => {
		if (userTask.assignee === currentUsername) {
			return t('tasklist.caseTaskAssignedToYou');
		}

		if (typeof userTask.assignee === 'string') {
			return userTask.assignee;
		}

		const candidates = [...userTask.candidateGroups, ...userTask.candidateUsers];

		return candidates.length === 0 ? t('tasklist.caseTaskUnassigned') : candidates.join(', ');
	};
}

const ACTIVITY_STATUS_KEYS = {
	open: 'tasklist.caseActivityOpen',
	active: 'tasklist.caseActivityActive',
	completed: 'tasklist.caseActivityCompleted',
	notStarted: 'tasklist.caseActivityNotStarted',
} as const satisfies Record<ActivityStatus, string>;

type TaskRowProps = {
	name: string;
	description: string;
	icon: React.ReactNode;
	isDone?: boolean;
	action?: React.ReactNode;
};

const WorkRow: React.FC<TaskRowProps> = ({name, description, icon, isDone, action}) => (
	<li
		className={cn(
			'flex items-center gap-3 rounded-md border border-border px-3 py-2.5',
			isDone && 'bg-[color:var(--success-background-subtle)]',
		)}
	>
		{icon}
		<div className="flex min-w-0 flex-1 flex-col gap-0.5">
			<Text variant="label-md-strong" className="truncate text-neutral-foreground-strong">
				{name}
			</Text>
			<span className="truncate text-xs text-neutral-foreground-subtle">{description}</span>
		</div>
		{action}
	</li>
);

type Props = {
	stageName: string | null;
	work: StageWork;
	currentUsername: string;
	startingElementId: string | null;
	isCompletingStage: boolean;
	onOpenTask: (userTaskKey: string) => void;
	onStartActivity: (elementId: string) => void;
	onCompleteStage: () => void;
};

const CaseWorkCard: React.FC<Props> = ({
	stageName,
	work,
	currentUsername,
	startingElementId,
	isCompletingStage,
	onOpenTask,
	onStartActivity,
	onCompleteStage,
}) => {
	const {t} = useTranslation();
	const getAssignmentLabel = useAssignmentLabel();
	const {activities, openTasks, completion} = work;
	const completedCount = activities.filter(({status}) => status === 'completed').length;
	const hasWork = activities.length > 0 || openTasks.length > 0;

	const renderOpenButton = (userTask: UserTask) => (
		<Button variant="secondary" size="sm" onClick={() => onOpenTask(userTask.userTaskKey)}>
			{t('tasklist.caseOpenTask')}
			<ArrowRight aria-hidden />
		</Button>
	);

	const renderActivityAction = ({elementId, isRequired, status, userTask}: StageActivity) => {
		if (userTask !== null) {
			return renderOpenButton(userTask);
		}

		if (status === 'active') {
			return <LoaderCircle className="size-4 animate-spin text-neutral-foreground-subtle" aria-hidden />;
		}

		if (isRequired) {
			return null;
		}

		return (
			<Button
				variant="secondary"
				size="sm"
				disabled={startingElementId !== null}
				onClick={() => onStartActivity(elementId)}
			>
				{startingElementId === elementId ? <Spinner /> : null}
				{status === 'completed' ? t('tasklist.caseActivityStartAgain') : t('tasklist.caseActivityStart')}
			</Button>
		);
	};

	return (
		<Card>
			<CardHeader>
				<CardDescription className="text-xs font-semibold tracking-wide uppercase">
					{stageName === null ? t('tasklist.caseOpenWork') : t('tasklist.caseCurrentStage')}
				</CardDescription>
				<CardTitle className="text-lg">{stageName ?? t('tasklist.caseAllOpenTasks')}</CardTitle>
			</CardHeader>
			<CardContent className="flex flex-col gap-5">
				{hasWork ? null : (
					<EmptyState
						size="sm"
						heading={stageName === null ? t('tasklist.caseNoOpenWorkInCase') : t('tasklist.caseNoOpenWork')}
					/>
				)}

				{openTasks.length === 0 ? null : (
					<section className="flex flex-col gap-2" aria-label={t('tasklist.caseTasksLabel')}>
						<Heading as="h3" variant="label-sm" className="tracking-wide text-neutral-foreground-subtle uppercase">
							{t('tasklist.caseTasksLabel')}
						</Heading>
						<ul className="flex flex-col gap-2">
							{openTasks.map((userTask) => (
								<WorkRow
									key={userTask.userTaskKey}
									name={userTask.name || userTask.elementId}
									description={getAssignmentLabel(userTask, currentUsername)}
									icon={<UserRound className="size-4 shrink-0 text-[color:var(--info-action-default)]" aria-hidden />}
									action={renderOpenButton(userTask)}
								/>
							))}
						</ul>
					</section>
				)}

				{activities.length === 0 ? null : (
					<section className="flex flex-col gap-2" aria-label={t('tasklist.caseActivitiesLabel')}>
						<Heading
							as="h3"
							variant="label-sm"
							className="flex items-baseline gap-2 tracking-wide text-neutral-foreground-subtle uppercase"
						>
							{t('tasklist.caseActivitiesLabel')}
							<span className="font-normal tracking-normal normal-case">
								{t('tasklist.caseActivitiesCount', {completed: completedCount, total: activities.length})}
							</span>
						</Heading>
						<ul className="flex flex-col gap-2">
							{activities.map((activity) => (
								<WorkRow
									key={activity.elementId}
									name={activity.name}
									description={[
										activity.isRequired ? t('tasklist.caseActivityRequired') : t('tasklist.caseActivityOptional'),
										t(ACTIVITY_STATUS_KEYS[activity.status]),
									].join(' · ')}
									icon={
										activity.status === 'completed' ? (
											<CircleCheck className="size-4 shrink-0 text-[color:var(--success-action-default)]" aria-hidden />
										) : (
											<CirclePlay className="size-4 shrink-0 text-neutral-foreground-subtle" aria-hidden />
										)
									}
									isDone={activity.status === 'completed'}
									action={renderActivityAction(activity)}
								/>
							))}
						</ul>
					</section>
				)}
			</CardContent>
			{completion === null ? null : (
				<CardFooter className="flex items-center justify-between gap-4 border-t border-border">
					<span className="text-xs text-neutral-foreground-subtle">
						{completion.correlationKey === null
							? t('tasklist.caseStageDoneUnresolvable')
							: completion.isAllowed
								? t('tasklist.caseStageDoneHint')
								: t('tasklist.caseStageDoneBlocked')}
					</span>
					<Button
						disabled={!completion.isAllowed || completion.correlationKey === null || isCompletingStage}
						onClick={onCompleteStage}
					>
						{isCompletingStage ? <Spinner /> : <Check aria-hidden />}
						{t('tasklist.caseStageDoneButton')}
					</Button>
				</CardFooter>
			)}
		</Card>
	);
};

export {CaseWorkCard};
