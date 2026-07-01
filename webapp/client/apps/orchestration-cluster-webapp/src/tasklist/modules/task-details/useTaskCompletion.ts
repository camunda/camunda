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
	taskState,
	onComplete,
}: {
	userTaskKey: string;
	taskState: UserTask['state'];
	onComplete: () => void;
}) {
	const queryClient = useQueryClient();

	const actorRef = useActorRef(taskCompletionMachine, {
		input: {queryClient, userTaskKey, initialTaskState: taskState},
	});

	useEffect(() => {
		const subscription = actorRef.on('task.completed', onComplete);

		return () => {
			subscription.unsubscribe();
		};
	}, [actorRef, onComplete]);

	const status = useSelector(actorRef, deriveCompletionStatus);
	const isBusy = status === 'active';
	const complete = useCallback(() => actorRef.send({type: 'task.complete'}), [actorRef]);

	return {
		status,
		isBusy,
		complete,
	};
}

export {useTaskCompletion};
export type {CompletionStatus};
