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
import {
	ArrowRight,
	Check,
	CircleAlert,
	CircleCheck,
	CirclePlay,
	Clock,
	Cog,
	LoaderCircle,
	Mail,
	Radio,
	Split,
	Table,
	UserRound,
	Workflow,
} from '@camunda/design-system/icons';
import type {ProcessInstance, UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';
import type {AutomationKind, StageTask, StageWork, TaskStatus} from '#/tasklist/modules/case-details/getStageWork';
import type {CaseWorkStatus} from '#/tasklist/modules/case-details/useCaseWork';

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
} as const satisfies Record<TaskStatus, string>;

const AUTOMATION_KIND_KEYS = {
	automated: 'tasklist.caseAutomationAutomated',
	decision: 'tasklist.caseAutomationDecision',
	calledProcess: 'tasklist.caseAutomationCalledProcess',
	timer: 'tasklist.caseAutomationTimer',
	message: 'tasklist.caseAutomationMessage',
	signal: 'tasklist.caseAutomationSignal',
	event: 'tasklist.caseAutomationEvent',
} as const satisfies Record<AutomationKind, string>;

const AUTOMATION_ICONS = {
	automated: Cog,
	decision: Table,
	calledProcess: Workflow,
	timer: Clock,
	message: Mail,
	signal: Radio,
	event: Split,
} as const satisfies Record<AutomationKind, React.ComponentType<{className?: string}>>;

/** A step that waits for an event, rather than doing work. */
const WAITING_KINDS = new Set<AutomationKind>(['timer', 'message', 'signal', 'event']);

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
	caseState: ProcessInstance['state'];
	stageName: string | null;
	isStageBlocked: boolean;
	work: StageWork;
	currentUsername: string;
	caseWorkStatus: CaseWorkStatus;
	/** The optional task that the case worker opens, while the case starts it. */
	openingElementId: string | null;
	onOpenTask: (userTaskKey: string) => void;
	onStartTask: (elementId: string) => void;
	onCompleteStage: () => void;
};

const CaseWorkCard: React.FC<Props> = ({
	caseState,
	stageName,
	isStageBlocked,
	work,
	currentUsername,
	caseWorkStatus,
	openingElementId,
	onOpenTask,
	onStartTask,
	onCompleteStage,
}) => {
	const {t} = useTranslation();
	const getAssignmentLabel = useAssignmentLabel();
	const {tasks, isAdHoc, completion} = work;
	const isBusy = caseWorkStatus !== 'idle';
	const isClosingStage = caseWorkStatus === 'completingStage' || caseWorkStatus === 'waitingForNextStage';
	const adHocTasks = tasks.filter(({isAdHocTask}) => isAdHocTask);
	const completedCount = adHocTasks.filter((task) => task.completedCount > 0).length;

	// The searches can show the next stage before the stage completion settles, so the footer stays
	// until the state machine is idle again.
	const getCompletionHint = (): string => {
		if (caseWorkStatus === 'completingStage') {
			return t('tasklist.caseStageClosing');
		}

		if (caseWorkStatus === 'waitingForNextStage') {
			return t('tasklist.caseStageWaitingForNext');
		}

		if (completion === null) {
			return '';
		}

		if (completion.correlationKey === null) {
			return t('tasklist.caseStageDoneUnresolvable');
		}

		return completion.isAllowed ? t('tasklist.caseStageDoneHint') : t('tasklist.caseStageDoneBlocked');
	};

	const renderOpenButton = (userTask: UserTask) => (
		<Button variant="secondary" size="sm" disabled={isBusy} onClick={() => onOpenTask(userTask.userTaskKey)}>
			{t('tasklist.caseOpenTask')}
			<ArrowRight aria-hidden />
		</Button>
	);

	const getStatusLabel = (task: StageTask): string => {
		if (task.userTask !== null) {
			return getAssignmentLabel(task.userTask, currentUsername);
		}

		if (task.status === 'active' && task.automation !== null) {
			if (task.automation.hasIncident) {
				return t('tasklist.caseAutomationIncident');
			}

			return WAITING_KINDS.has(task.automation.kind)
				? t('tasklist.caseAutomationWaiting')
				: t(ACTIVITY_STATUS_KEYS.active);
		}

		return task.status === 'completed' && task.completedCount > 1
			? t('tasklist.caseActivityCompletedTimes', {count: task.completedCount})
			: t(ACTIVITY_STATUS_KEYS[task.status]);
	};

	// A running step of a sequential stage is not required or optional: the stage simply waits for it.
	const getTaskDescription = (task: StageTask): string =>
		[
			task.automation === null || task.isAdHocTask
				? task.isRequired
					? t('tasklist.caseActivityRequired')
					: t('tasklist.caseActivityOptional')
				: null,
			task.automation === null ? null : t(AUTOMATION_KIND_KEYS[task.automation.kind]),
			getStatusLabel(task),
			task.status === 'active' && task.automation !== null && task.automation.runningCount > 1
				? t('tasklist.caseAutomationInstances', {count: task.automation.runningCount})
				: null,
			task.status !== 'completed' && task.completedCount > 0
				? t('tasklist.caseActivityCompletedBefore', {count: task.completedCount})
				: null,
		]
			.filter((part) => part !== null)
			.join(' · ');

	const getTaskIcon = ({completedCount: timesCompleted, userTask, automation, status}: StageTask) => {
		if (timesCompleted > 0 && status !== 'active') {
			return <CircleCheck className="size-4 shrink-0 text-[color:var(--success-action-default)]" aria-hidden />;
		}

		if (automation !== null) {
			if (status === 'active' && automation.hasIncident) {
				return <CircleAlert className="size-4 shrink-0 text-[color:var(--danger-border-strong)]" aria-hidden />;
			}

			const AutomationIcon = AUTOMATION_ICONS[automation.kind];

			return <AutomationIcon className="size-4 shrink-0 text-neutral-foreground-subtle" aria-hidden />;
		}

		if (timesCompleted > 0) {
			return <CircleCheck className="size-4 shrink-0 text-[color:var(--success-action-default)]" aria-hidden />;
		}

		return userTask === null ? (
			<CirclePlay className="size-4 shrink-0 text-neutral-foreground-subtle" aria-hidden />
		) : (
			<UserRound className="size-4 shrink-0 text-[color:var(--info-action-default)]" aria-hidden />
		);
	};

	const renderTaskAction = ({elementId, canStart, status, userTask, automation}: StageTask) => {
		if (userTask !== null) {
			return renderOpenButton(userTask);
		}

		if (status === 'active' && automation?.hasIncident === true) {
			return null;
		}

		if (status === 'active') {
			return <LoaderCircle className="size-4 animate-spin text-neutral-foreground-subtle" aria-hidden />;
		}

		if (!canStart) {
			return null;
		}

		return (
			<Button
				variant="secondary"
				size="sm"
				disabled={isBusy}
				aria-busy={openingElementId === elementId}
				onClick={() => onStartTask(elementId)}
			>
				{status === 'completed' ? t('tasklist.caseActivityStartAgain') : t('tasklist.caseOpenTask')}
				{openingElementId === elementId ? <Spinner /> : <ArrowRight aria-hidden />}
			</Button>
		);
	};

	if (caseState === 'COMPLETED' || caseState === 'TERMINATED') {
		return (
			<Card>
				<CardHeader>
					<CardTitle className="text-lg">
						{caseState === 'COMPLETED' ? t('tasklist.caseClosedCompleted') : t('tasklist.caseClosedCancelled')}
					</CardTitle>
					<CardDescription>{t('tasklist.caseClosedDescription')}</CardDescription>
				</CardHeader>
			</Card>
		);
	}

	return (
		<Card>
			<CardHeader>
				<CardDescription className="text-xs font-semibold tracking-wide uppercase">
					{stageName === null ? t('tasklist.caseOpenWork') : t('tasklist.caseCurrentStage')}
				</CardDescription>
				<CardTitle className="text-lg">{stageName ?? t('tasklist.caseAllOpenTasks')}</CardTitle>
			</CardHeader>
			<CardContent className="flex flex-col gap-5">
				{tasks.length === 0 ? (
					<EmptyState
						size="sm"
						heading={
							isStageBlocked
								? t('tasklist.caseStageBlockedNoWork')
								: stageName === null
									? t('tasklist.caseNoOpenWorkInCase')
									: t('tasklist.caseNoOpenWork')
						}
					/>
				) : (
					<section className="flex flex-col gap-2" aria-label={t('tasklist.caseTasksLabel')}>
						<Heading
							as="h3"
							variant="label-sm"
							className="flex items-baseline gap-2 tracking-wide text-neutral-foreground-subtle uppercase"
						>
							{t('tasklist.caseTasksLabel')}
							{isAdHoc ? (
								<span className="font-normal tracking-normal normal-case">
									{t('tasklist.caseActivitiesCount', {completed: completedCount, total: adHocTasks.length})}
								</span>
							) : null}
						</Heading>
						<ul className="flex flex-col gap-2">
							{tasks.map((task) => (
								<WorkRow
									key={task.key}
									name={task.name}
									description={getTaskDescription(task)}
									icon={getTaskIcon(task)}
									isDone={task.status === 'completed'}
									action={renderTaskAction(task)}
								/>
							))}
						</ul>
					</section>
				)}
			</CardContent>
			{completion === null && !isClosingStage ? null : (
				<CardFooter className="flex items-center justify-between gap-4 border-t border-border">
					<span className="text-xs text-neutral-foreground-subtle" role="status">
						{getCompletionHint()}
					</span>
					<Button
						disabled={
							isClosingStage || completion === null || !completion.isAllowed || completion.correlationKey === null
						}
						aria-busy={isClosingStage}
						onClick={onCompleteStage}
					>
						{isClosingStage ? <Spinner /> : <Check aria-hidden />}
						{t('tasklist.caseStageDoneButton')}
					</Button>
				</CardFooter>
			)}
		</Card>
	);
};

export {CaseWorkCard};
