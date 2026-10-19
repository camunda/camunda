/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect} from 'react';
import {useActorRef, useSelector} from '@xstate/react';
import {useQueryClient} from '@tanstack/react-query';
import type {SnapshotFrom} from 'xstate';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {
	caseWorkMachine,
	type ActivityRequest,
	type StageCompletionRequest,
} from '#/tasklist/modules/case-details/caseWorkMachine';

type CaseWorkStatus = 'idle' | 'openingActivity' | 'completingStage' | 'waitingForNextStage';

function deriveStatus(snapshot: SnapshotFrom<typeof caseWorkMachine>): CaseWorkStatus {
	if (snapshot.hasTag('status:opening_activity')) {
		return 'openingActivity';
	}

	if (snapshot.hasTag('status:completing_stage')) {
		return 'completingStage';
	}

	return snapshot.hasTag('status:waiting_for_next_stage') ? 'waitingForNextStage' : 'idle';
}

function useCaseWork({
	caseRef,
	onOpenTask,
}: {
	caseRef: Pick<ProcessInstance, 'processInstanceKey' | 'businessId' | 'tenantId'>;
	onOpenTask: (userTaskKey: string) => void;
}) {
	const queryClient = useQueryClient();
	const actorRef = useActorRef(caseWorkMachine, {input: {queryClient, caseRef}});
	const status = useSelector(actorRef, deriveStatus);
	const openingElementId = useSelector(actorRef, (snapshot) => snapshot.context.activity?.elementId ?? null);

	useEffect(() => {
		const subscription = actorRef.on('task.opened', ({userTaskKey}) => onOpenTask(userTaskKey));

		return () => {
			subscription.unsubscribe();
		};
	}, [actorRef, onOpenTask]);

	const openActivity = useCallback(
		(activity: ActivityRequest) => actorRef.send({type: 'activity.open', ...activity}),
		[actorRef],
	);
	const completeStage = useCallback(
		(stage: StageCompletionRequest) => actorRef.send({type: 'stage.complete', ...stage}),
		[actorRef],
	);

	return {status, openingElementId, openActivity, completeStage};
}

export {useCaseWork};
export type {CaseWorkStatus};
