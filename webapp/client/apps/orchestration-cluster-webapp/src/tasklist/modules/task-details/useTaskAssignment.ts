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
		snapshot.matches('assigning') ||
		snapshot.matches('assignmentDelayed') ||
		snapshot.matches('pollingAssignment') ||
		snapshot.matches('awaitingAssignment')
	) {
		return 'assigning';
	}

	if (
		snapshot.matches('unassigning') ||
		snapshot.matches('unassignmentDelayed') ||
		snapshot.matches('pollingUnassignment') ||
		snapshot.matches('awaitingUnassignment')
	) {
		return 'unassigning';
	}

	if (snapshot.matches('assignmentSucceeded')) {
		return 'assignmentSuccessful';
	}

	if (snapshot.matches('unassignmentSucceeded')) {
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
				type: 'ASSIGN',
			}),
		[send],
	);
	const unassign = useCallback(
		() =>
			send({
				type: 'UNASSIGN',
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
