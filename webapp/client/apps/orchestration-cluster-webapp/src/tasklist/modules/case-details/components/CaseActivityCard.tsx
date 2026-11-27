/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import type {TFunction} from 'i18next';
import type {CaseModel} from '@camunda/case-management-sdk';
import type {ElementInstance, UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {
	Card,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
	EmptyState,
	Skeleton,
	Text,
} from '@camunda/design-system';
import {
	Bot,
	CircleCheck,
	CirclePause,
	CirclePlay,
	CircleX,
	Database,
	FileText,
	Flag,
	GitBranch,
	Pencil,
	Save,
	UserCheck,
	UserX,
	Workflow,
	Wrench,
} from '@camunda/design-system/icons';
import {useCaseAuditLogs} from '#/tasklist/modules/case-details/caseData';
import {
	getCaseActivity,
	type CaseActivity,
	type CaseActivityKind,
} from '#/tasklist/modules/case-details/getCaseActivity';
import {formatHistoryDate} from '#/tasklist/modules/task-details-history/formatHistoryDate';

const ICONS = {
	caseStarted: Flag,
	caseCancelled: CircleX,
	caseSuspended: CirclePause,
	caseResumed: CirclePlay,
	caseModified: Workflow,
	caseMigrated: GitBranch,
	taskAssigned: UserCheck,
	taskUnassigned: UserX,
	taskUpdated: Pencil,
	taskCompleted: CircleCheck,
	taskDraftSaved: Save,
	dataChanged: Database,
	documentsChanged: FileText,
	incidentResolved: Wrench,
	stepCompleted: Bot,
} as const satisfies Record<CaseActivityKind, React.ComponentType<{className?: string}>>;

function getSummary(t: TFunction, {kind, elementName, assignee}: CaseActivity) {
	const task = elementName ?? t('tasklist.caseActivityUnknownTask');

	switch (kind) {
		case 'caseStarted':
			return t('tasklist.caseActivityCaseStarted');
		case 'caseCancelled':
			return t('tasklist.caseActivityCaseCancelled');
		case 'caseSuspended':
			return t('tasklist.caseActivityCaseSuspended');
		case 'caseResumed':
			return t('tasklist.caseActivityCaseResumed');
		case 'caseModified':
			return t('tasklist.caseActivityCaseModified');
		case 'caseMigrated':
			return t('tasklist.caseActivityCaseMigrated');
		case 'taskAssigned':
			return t('tasklist.caseActivityTaskAssigned', {task, assignee});
		case 'taskUnassigned':
			return t('tasklist.caseActivityTaskUnassigned', {task});
		case 'taskUpdated':
			return t('tasklist.caseActivityTaskUpdated', {task});
		case 'taskCompleted':
			return t('tasklist.caseActivityTaskCompleted', {task});
		case 'taskDraftSaved':
			return t('tasklist.caseActivityTaskDraftSaved', {task});
		case 'dataChanged':
			return t('tasklist.caseActivityDataChanged');
		case 'documentsChanged':
			return t('tasklist.caseActivityDocumentsChanged');
		case 'incidentResolved':
			return elementName === null
				? t('tasklist.caseActivityIncidentResolved')
				: t('tasklist.caseActivityIncidentResolvedIn', {task: elementName});
		case 'stepCompleted':
			return t('tasklist.caseActivityStepCompleted', {
				step: elementName ?? t('tasklist.caseActivityUnknownStep'),
			});
	}
}

function getActor(t: TFunction, {actorId, actorType}: CaseActivity) {
	if (actorId === null) {
		return t('tasklist.caseActivityUnknownActor');
	}

	return actorType === 'CLIENT' ? t('tasklist.caseActivityClientActor', {clientId: actorId}) : actorId;
}

const ActivityRow: React.FC<{activity: CaseActivity}> = ({activity}) => {
	const {t} = useTranslation();
	const Icon = ICONS[activity.kind];
	const details = [
		activity.stageName === null ? null : t('tasklist.caseActivityStage', {stage: activity.stageName}),
		activity.variableNames.length === 0
			? null
			: t('tasklist.caseActivityVariables', {names: activity.variableNames.join(', ')}),
	].filter((detail) => detail !== null);

	return (
		<li className="flex gap-3 py-2.5 first:pt-0 last:pb-0">
			<Icon className="mt-0.5 size-4 shrink-0 text-neutral-foreground-subtle" aria-hidden />
			<div className="flex min-w-0 flex-1 flex-col gap-0.5">
				<Text variant="label-md-strong" className="text-neutral-foreground-strong">
					{getSummary(t, activity)}
				</Text>
				{details.length === 0 ? null : (
					<span className="text-xs break-words text-neutral-foreground-subtle">{details.join(' · ')}</span>
				)}
				<span className="text-xs text-neutral-foreground-subtle">
					{getActor(t, activity)} · <time dateTime={activity.timestamp}>{formatHistoryDate(activity.timestamp)}</time>
				</span>
			</div>
		</li>
	);
};

type Props = {
	processInstanceKey: string;
	model: CaseModel;
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
	/** The variables that hold document references. */
	documentVariableNames: Set<string>;
};

const CaseActivityCard: React.FC<Props> = ({
	processInstanceKey,
	model,
	elementInstances,
	userTasks,
	documentVariableNames,
}) => {
	const {t} = useTranslation();
	const {data, isPending, isError} = useCaseAuditLogs(processInstanceKey);
	const activities = useMemo(
		() =>
			data === undefined
				? []
				: getCaseActivity({auditLogs: data.items, model, elementInstances, userTasks, documentVariableNames}),
		[data, model, elementInstances, userTasks, documentVariableNames],
	);

	return (
		<Card>
			<CardHeader>
				<CardTitle>{t('tasklist.caseActivityTitle')}</CardTitle>
				<CardDescription>{t('tasklist.caseActivityDescription')}</CardDescription>
			</CardHeader>
			<CardContent>
				{isPending ? (
					<div className="flex flex-col gap-3" aria-busy="true">
						{Array.from({length: 3}, (_, index) => (
							<Skeleton key={index} className="h-10 w-full" />
						))}
					</div>
				) : isError && data === undefined ? (
					<EmptyState size="sm" heading={t('tasklist.caseActivityLoadFailed')} />
				) : activities.length === 0 ? (
					<EmptyState size="sm" heading={t('tasklist.caseActivityEmpty')} />
				) : (
					<ol
						className="flex max-h-[32rem] flex-col divide-y divide-border overflow-y-auto"
						aria-label={t('tasklist.caseActivityTitle')}
					>
						{activities.map((activity) => (
							<ActivityRow key={activity.key} activity={activity} />
						))}
					</ol>
				)}
			</CardContent>
			{data !== undefined && data.page.totalItems > data.items.length ? (
				<CardFooter>
					<span className="text-xs text-neutral-foreground-subtle">
						{t('tasklist.caseActivityLatestEntries', {count: data.items.length})}
					</span>
				</CardFooter>
			) : null}
		</Card>
	);
};

export {CaseActivityCard};
