/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {useActorRef, useSelector} from '@xstate/react';
import {useQueryClient} from '@tanstack/react-query';
import type {SnapshotFrom} from 'xstate';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {
	stageCompletionMachine,
	type StageCompletionRequest,
} from '#/tasklist/modules/case-details/stageCompletionMachine';

type StageCompletionStatus = 'idle' | 'completing' | 'waitingForNextStage';

function deriveStatus(snapshot: SnapshotFrom<typeof stageCompletionMachine>): StageCompletionStatus {
	if (snapshot.hasTag('status:completing')) {
		return 'completing';
	}

	return snapshot.hasTag('status:waiting_for_next_stage') ? 'waitingForNextStage' : 'idle';
}

function useStageCompletion(caseRef: Pick<ProcessInstance, 'processInstanceKey' | 'businessId' | 'tenantId'>) {
	const queryClient = useQueryClient();
	const actorRef = useActorRef(stageCompletionMachine, {input: {queryClient, caseRef}});
	const status = useSelector(actorRef, deriveStatus);
	const closingStageName = useSelector(actorRef, (snapshot) => snapshot.context.request?.stageName ?? null);
	const completeStage = useCallback(
		(request: StageCompletionRequest) => actorRef.send({type: 'stage.complete', ...request}),
		[actorRef],
	);

	return {status, closingStageName, completeStage};
}

export {useStageCompletion};
export type {StageCompletionStatus};
