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
import {taskAssignmentMachine} from './taskAssignmentMachine';
import {useCallback} from 'react';

type AssignmentStatus = 'off' | 'assigning' | 'unassigning' | 'assignmentSuccessful' | 'unassignmentSuccessful';

function deriveAssignmentStatus(snapshot: SnapshotFrom<typeof taskAssignmentMachine>): AssignmentStatus {
	if (snapshot.hasTag('status:assigning')) {
		return 'assigning';
	}

	if (snapshot.hasTag('status:unassigning')) {
		return 'unassigning';
	}

	if (snapshot.hasTag('status:assignment_successful')) {
		return 'assignmentSuccessful';
	}

	if (snapshot.hasTag('status:unassignment_successful')) {
		return 'unassignmentSuccessful';
	}

	return 'off';
}

function useTaskAssignment({
	userTaskKey,
	currentUser,
	taskState,
	assignee,
}: {
	userTaskKey: string;
	currentUser: string;
	taskState: UserTask['state'];
	assignee: string | null;
}) {
	const queryClient = useQueryClient();

	const actorRef = useActorRef(taskAssignmentMachine, {
		input: {queryClient, userTaskKey, currentUser, initialTaskState: taskState, initialAssignee: assignee},
	});

	const status = useSelector(actorRef, deriveAssignmentStatus);
	const isBusy = status === 'assigning' || status === 'unassigning';
	const toggle = useCallback(
		() =>
			actorRef.send({
				type: 'task.toggle',
				taskState,
				assignee,
			}),
		[actorRef, taskState, assignee],
	);

	return {
		status,
		isBusy,
		toggle,
	};
}

export {useTaskAssignment};
export type {AssignmentStatus};
