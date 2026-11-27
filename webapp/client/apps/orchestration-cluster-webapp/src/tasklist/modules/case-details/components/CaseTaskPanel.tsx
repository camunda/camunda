/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useQueryClient} from '@tanstack/react-query';
import {
	Button,
	Card,
	CardAction,
	CardContent,
	CardDescription,
	CardFooter,
	CardHeader,
	CardTitle,
	toast,
} from '@camunda/design-system';
import {X} from '@camunda/design-system/icons';
import type {AuthenticatedCurrentUser, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {AssignButton} from '#/tasklist/modules/task-details/components/AssignButton';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/components/CompleteTaskButton';
import {useTaskCompletion} from '#/tasklist/modules/task-details/useTaskCompletion';
import {TaskDetailsForm} from '#/tasklist/modules/task-details-form/components/TaskDetailsForm';
import {saveTaskDraft, syncTaskDraft} from '#/tasklist/modules/case-details/taskDraft';

type Props = {
	task: UserTask;
	currentUser: AuthenticatedCurrentUser;
	formSchema: string | null;
	variables: Variable[];
	onCompleted: () => void;
	onClose: () => void;
};

/** Works a task inside the case page, so that the case worker stays in the case. */
const CaseTaskPanel: React.FC<Props> = ({task, currentUser, formSchema, variables, onCompleted, onClose}) => {
	const {t} = useTranslation();
	const {status, isCompletionAllowed, isHidden, complete} = useTaskCompletion({
		userTaskKey: task.userTaskKey,
		currentUser: currentUser.username,
		taskState: task.state,
		assignee: task.assignee ?? null,
		isSuspended: task.isSuspended,
		onComplete: onCompleted,
	});
	const queryClient = useQueryClient();
	// The variables that a draft holds in the task's local scope, see syncTaskDraft.
	const [draftVariableNames, setDraftVariableNames] = useState(
		() => new Set(variables.filter(({scopeKey}) => scopeKey === task.elementInstanceKey).map(({name}) => name)),
	);
	const saveDraft = useCallback(
		async (draft: Record<string, unknown>) => {
			const isVisible = await saveTaskDraft(queryClient, task, draft);
			setDraftVariableNames((names) => new Set([...names, ...Object.keys(draft)]));
			toast.success(t(isVisible ? 'tasklist.caseTaskDraftSaved' : 'tasklist.caseTaskDraftSavedNotVisibleYet'));
		},
		[queryClient, task, t],
	);
	const submit = useCallback(
		async (completionVariables: Record<string, unknown>) => {
			try {
				await syncTaskDraft(task, draftVariableNames, completionVariables);
			} catch (error) {
				toast.error(t('tasklist.caseTaskDraftSyncFailed'));
				throw error;
			}

			complete(completionVariables);
		},
		[task, draftVariableNames, complete, t],
	);

	return (
		<Card className="gap-0 border-[color:var(--info-action-default)] pb-0">
			<CardHeader className="pb-4">
				<CardDescription className="text-xs font-semibold tracking-wide uppercase">
					{t('tasklist.caseCurrentTask')}
				</CardDescription>
				<CardTitle className="text-lg">{task.name || task.elementId}</CardTitle>
				<CardAction className="flex items-center gap-2">
					<AssignButton
						userTaskKey={task.userTaskKey}
						assignee={task.assignee ?? null}
						taskState={task.state}
						currentUser={currentUser.username}
						isSuspended={task.isSuspended}
					/>
					<Button variant="ghost" size="sm" aria-label={t('tasklist.caseCloseTask')} onClick={onClose}>
						<X aria-hidden />
					</Button>
				</CardAction>
			</CardHeader>
			{formSchema === null ? (
				<>
					<CardContent className="pb-4 text-sm text-neutral-foreground-subtle">
						{t('tasklist.caseTaskWithoutForm')}
					</CardContent>
					<CardFooter className="flex justify-end border-t border-border py-3">
						<CompleteTaskButton
							status={status}
							isHidden={isHidden}
							isDisabled={!isCompletionAllowed}
							isSuspended={task.isSuspended}
							onClick={() => complete()}
						/>
					</CardFooter>
				</>
			) : (
				<CardContent className="px-0">
					<TaskDetailsForm
						formSchema={formSchema}
						variables={variables}
						completionStatus={status}
						isCompletionAllowed={isCompletionAllowed}
						isHidden={isHidden}
						isSuspended={task.isSuspended}
						onSubmit={submit}
						onSaveDraft={saveDraft}
					/>
				</CardContent>
			)}
		</Card>
	);
};

export {CaseTaskPanel};
