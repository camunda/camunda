/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useActorRef, useSelector} from '@xstate/react';
import {useQueryClient} from '@tanstack/react-query';
import type {SnapshotFrom} from 'xstate';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import type {InlineLoadingProps} from '@carbon/react';
import {useCallback, useEffect} from 'react';
import {taskCompletionMachine} from './taskCompletionMachine';

type CompletionStatus = NonNullable<InlineLoadingProps['status']>;

function deriveCompletionStatus(snapshot: SnapshotFrom<typeof taskCompletionMachine>): CompletionStatus {
	if (snapshot.hasTag('status:completing')) {
		return 'active';
	}

	if (snapshot.hasTag('status:completion_successful')) {
		return 'finished';
	}

	if (snapshot.hasTag('status:completion_failed')) {
		return 'error';
	}

	return 'inactive';
}

function useTaskCompletion({
	userTaskKey,
	currentUser,
	taskState,
	assignee,
	onComplete,
}: {
	userTaskKey: string;
	currentUser: string;
	taskState: UserTask['state'];
	assignee: string | null;
	onComplete: () => void;
}) {
	const queryClient = useQueryClient();

	const actorRef = useActorRef(taskCompletionMachine, {
		input: {queryClient, userTaskKey, currentUser, initialTaskState: taskState, initialAssignee: assignee},
	});

	useEffect(() => {
		actorRef.send({type: 'task.updated', taskState, assignee});
	}, [actorRef, taskState, assignee]);

	useEffect(() => {
		const subscription = actorRef.on('task.completed', onComplete);

		return () => {
			subscription.unsubscribe();
		};
	}, [actorRef, onComplete]);

	const status = useSelector(actorRef, deriveCompletionStatus);
	const isCompletionAllowed = useSelector(actorRef, (snapshot) => snapshot.can({type: 'task.complete'}));
	const isHidden = useSelector(actorRef, (snapshot) => snapshot.context.taskState === 'COMPLETED');
	const isBusy = status === 'active';
	const complete = useCallback(
		(variables: Record<string, unknown> = {}) => actorRef.send({type: 'task.complete', variables}),
		[actorRef],
	);

	return {
		status,
		isCompletionAllowed,
		isHidden,
		isBusy,
		complete,
	};
}

export {useTaskCompletion};
export type {CompletionStatus};
