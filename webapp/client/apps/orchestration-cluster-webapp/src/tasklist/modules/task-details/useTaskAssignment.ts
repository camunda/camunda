/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMachine} from '@xstate/react';
import {useQueryClient} from '@tanstack/react-query';
import type {SnapshotFrom} from 'xstate';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {taskAssignmentMachine} from './taskAssignmentMachine';
import {useCallback} from 'react';

type AssignmentStatus = 'off' | 'assigning' | 'unassigning' | 'assignmentSuccessful' | 'unassignmentSuccessful';

function deriveAssignmentStatus(snapshot: SnapshotFrom<typeof taskAssignmentMachine>): AssignmentStatus {
	if (
		snapshot.matches('Assigning') ||
		snapshot.matches('AssignmentDelayed') ||
		snapshot.matches('PollingAssignment') ||
		snapshot.matches('AwaitingAssignment')
	) {
		return 'assigning';
	}

	if (
		snapshot.matches('Unassigning') ||
		snapshot.matches('UnassignmentDelayed') ||
		snapshot.matches('PollingUnassignment') ||
		snapshot.matches('AwaitingUnassignment')
	) {
		return 'unassigning';
	}

	if (snapshot.matches('AssignmentSucceeded')) {
		return 'assignmentSuccessful';
	}

	if (snapshot.matches('UnassignmentSucceeded')) {
		return 'unassignmentSuccessful';
	}

	return 'off';
}

function useTaskAssignment({
	userTaskKey,
	currentUser,
	initialTaskState,
	initialAssignee,
}: {
	userTaskKey: string;
	currentUser: string;
	initialTaskState: UserTask['state'];
	initialAssignee: string | null;
}) {
	const queryClient = useQueryClient();

	const [snapshot, send] = useMachine(taskAssignmentMachine, {
		input: {queryClient, userTaskKey, currentUser, initialTaskState, initialAssignee},
	});

	const status = deriveAssignmentStatus(snapshot);
	const isBusy = status === 'assigning' || status === 'unassigning';
	const assign = useCallback(
		() =>
			send({
				type: 'task.assign',
			}),
		[send],
	);
	const unassign = useCallback(
		() =>
			send({
				type: 'task.unassign',
			}),
		[send],
	);

	return {
		status,
		isBusy,
		assign,
		unassign,
	};
}

export {useTaskAssignment};
export type {AssignmentStatus};
