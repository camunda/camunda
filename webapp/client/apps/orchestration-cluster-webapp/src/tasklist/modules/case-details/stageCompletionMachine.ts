/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {assign, fromPromise, setup} from 'xstate';
import {t} from 'i18next';
import type {QueryClient} from '@tanstack/react-query';
import type {ElementInstance, ProcessInstance, UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {toast} from '@camunda/design-system';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {request} from '#/shared/http/request';
import {
	getCaseElementInstancesRequestBody,
	getCaseUserTasksRequestBody,
	isTaskOfCase,
} from '#/tasklist/modules/case-details/getCaseRequestBodies';
import type {StageCompletion} from '#/tasklist/modules/case-details/getStageWork';

const MAX_POLLS = 20;

type CaseRef = Pick<ProcessInstance, 'processInstanceKey' | 'businessId' | 'tenantId'>;

type StageCompletionRequest = {
	stageName: string;
	/** The element instance of the stage that the case worker closes. */
	stageInstanceKey: string;
	/** The element IDs of all stages of the case, to detect the next stage. */
	stageIds: string[];
	completion: StageCompletion;
};

type MachineInput = {
	queryClient: QueryClient;
	caseRef: CaseRef;
};

type MachineContext = MachineInput & {
	request: StageCompletionRequest | null;
	pollCount: number;
};

type StageCompletionEvent = {type: 'stage.complete'} & StageCompletionRequest;

type StageCompletionTag = 'status:completing' | 'status:waiting_for_next_stage';

type CaseSnapshot = {
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
};

/**
 * The case has moved on when the closed stage is no longer active, and another stage is active or the case
 * ended. The user task search must also know every active user task. The searches are eventually
 * consistent, and each one can trail the others.
 */
function hasCaseMovedOn({elementInstances, userTasks}: CaseSnapshot, request: StageCompletionRequest): boolean {
	const isStageClosed = elementInstances.some(
		({elementInstanceKey, state}) => elementInstanceKey === request.stageInstanceKey && state !== 'ACTIVE',
	);
	const hasNextStage = elementInstances.some(
		({elementId, elementInstanceKey, state}) =>
			state === 'ACTIVE' && request.stageIds.includes(elementId) && elementInstanceKey !== request.stageInstanceKey,
	);
	const hasCaseEnded = elementInstances.every(({state}) => state !== 'ACTIVE');
	const searchedTaskKeys = new Set(userTasks.map(({elementInstanceKey}) => elementInstanceKey));
	const areTasksSearchable = elementInstances
		.filter(({type, state}) => type === 'USER_TASK' && state === 'ACTIVE')
		.every(({elementInstanceKey}) => searchedTaskKeys.has(elementInstanceKey));

	return isStageClosed && (hasNextStage || hasCaseEnded) && areTasksSearchable;
}

async function activateElement(adHocSubProcessInstanceKey: string, elementId: string): Promise<void> {
	const {error} = await request(
		endpoints.activateAdHocSubProcessActivities({adHocSubProcessInstanceKey, elements: [{elementId}]}),
	);

	if (error !== null) {
		throw error;
	}
}

/**
 * Completes a stage with the manual completion pattern of STAGES.md: activate "I am done", then let
 * "Stage done" catch the message.
 *
 * prototype: in the design, "I am done" publishes the message through a job worker. The case page
 * correlates the message itself, so that the demo runs without a worker.
 */
const completeStageLogic = fromPromise<void, {request: StageCompletionRequest; tenantId: string}>(async ({input}) => {
	const {stageInstanceKey, completion} = input.request;

	if (completion.correlationKey === null) {
		throw new Error('The correlation key of the stage cannot be resolved');
	}

	if (completion.doneElementId !== null) {
		await activateElement(stageInstanceKey, completion.doneElementId);
	}

	const {error} = await request(
		endpoints.correlateMessage({
			name: completion.messageName,
			correlationKey: completion.correlationKey,
			tenantId: input.tenantId,
		}),
	);

	if (error !== null) {
		throw error;
	}
});

/** Fetches the searches that the case page reads, so the page shows the new state as soon as it settles. */
const fetchCaseLogic = fromPromise<CaseSnapshot, {queryClient: QueryClient; caseRef: CaseRef}>(async ({input}) => {
	const {queryClient, caseRef} = input;
	const [elementInstances, userTasks] = await Promise.all([
		queryClient.fetchQuery({
			...queries.queryElementInstances(getCaseElementInstancesRequestBody(caseRef.processInstanceKey)),
			staleTime: 0,
		}),
		queryClient.fetchQuery({...queries.queryUserTasksPage(getCaseUserTasksRequestBody(caseRef)), staleTime: 0}),
	]);

	return {
		elementInstances: elementInstances.items,
		userTasks: userTasks.items.filter((task) => isTaskOfCase(task, caseRef.processInstanceKey)),
	};
});

const stageCompletionMachine = setup({
	types: {
		context: {} as MachineContext,
		input: {} as MachineInput,
		events: {} as StageCompletionEvent,
		tags: {} as StageCompletionTag,
	},
	actors: {
		completeStage: completeStageLogic,
		fetchCase: fetchCaseLogic,
	},
	guards: {
		hasCaseMovedOn: ({context}, params: {snapshot: CaseSnapshot}) =>
			context.request !== null && hasCaseMovedOn(params.snapshot, context.request),
		hasWaitedTooLong: ({context}) => context.pollCount >= MAX_POLLS,
	},
	actions: {
		storeRequest: assign({
			request: ({event}) => {
				const {type: _, ...stageRequest} = event;
				return stageRequest;
			},
			pollCount: 0,
		}),
		incrementPollCount: assign({pollCount: ({context}) => context.pollCount + 1}),
		clearRequest: assign({request: null}),
		refreshCase: ({context}) => {
			const {queryClient} = context;

			void queryClient.invalidateQueries({queryKey: ['processInstance']});
			void queryClient.invalidateQueries({queryKey: ['queryVariables']});
			void queryClient.invalidateQueries({queryKey: ['queryElementInstances']});
			void queryClient.invalidateQueries({queryKey: ['userTasks']});
		},
		notifyFailure: () => {
			toast.error(t('tasklist.caseStageDoneFailed'));
		},
		notifySlowCase: () => {
			toast.info(t('tasklist.caseStageDoneSlow'));
		},
	},
	delays: {
		POLL_DELAY: ({context}) => Math.min(500 * 2 ** context.pollCount, 2000),
	},
}).createMachine({
	id: 'stageCompletion',
	context: ({input}) => ({...input, request: null, pollCount: 0}),
	initial: 'Idle',
	states: {
		Idle: {
			on: {
				'stage.complete': {target: 'Completing', actions: 'storeRequest'},
			},
		},

		Completing: {
			tags: 'status:completing',
			invoke: {
				src: 'completeStage',
				input: ({context}) => ({request: context.request!, tenantId: context.caseRef.tenantId}),
				onDone: {target: 'WaitingForNextStage'},
				onError: {target: 'Idle', actions: ['notifyFailure', 'clearRequest', 'refreshCase']},
			},
		},

		WaitingForNextStage: {
			tags: 'status:waiting_for_next_stage',
			initial: 'Fetching',
			states: {
				Fetching: {
					invoke: {
						src: 'fetchCase',
						input: ({context}) => ({queryClient: context.queryClient, caseRef: context.caseRef}),
						onDone: [
							{
								guard: {type: 'hasCaseMovedOn', params: ({event}) => ({snapshot: event.output})},
								target: '#stageCompletion.Idle',
								actions: ['clearRequest', 'refreshCase'],
							},
							{target: 'Waiting', actions: 'incrementPollCount'},
						],
						onError: {target: 'Waiting', actions: 'incrementPollCount'},
					},
				},
				Waiting: {
					always: {
						guard: 'hasWaitedTooLong',
						target: '#stageCompletion.Idle',
						actions: ['notifySlowCase', 'clearRequest', 'refreshCase'],
					},
					after: {
						POLL_DELAY: {target: 'Fetching'},
					},
				},
			},
		},
	},
});

export {stageCompletionMachine};
export type {StageCompletionRequest};
