/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {assign, emit, fromPromise, setup} from 'xstate';
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

type ActivityRequest = {
	/** The element instance of the ad-hoc stage that holds the activity. */
	stageInstanceKey: string;
	elementId: string;
	/** A user task, or a sub-process with user tasks; the case worker opens its task. */
	expectsUserTask: boolean;
	/** The user tasks and element instances before the activation, to recognize the new ones. */
	knownTaskKeys: string[];
	knownElementInstanceKeys: string[];
};

type StageCompletionRequest = {
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
	activity: ActivityRequest | null;
	stage: StageCompletionRequest | null;
	pollCount: number;
};

type CaseWorkEvent = ({type: 'activity.open'} & ActivityRequest) | ({type: 'stage.complete'} & StageCompletionRequest);

type CaseWorkTag = 'status:opening_activity' | 'status:completing_stage' | 'status:waiting_for_next_stage';

type CaseSnapshot = {
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
};

type ActivityOutcome = {type: 'task'; userTaskKey: string} | {type: 'started'} | {type: 'pending'};

function getActivityOutcome({elementInstances, userTasks}: CaseSnapshot, activity: ActivityRequest): ActivityOutcome {
	const knownTaskKeys = new Set(activity.knownTaskKeys);
	const newTask = userTasks.find(({userTaskKey, state}) => state === 'CREATED' && !knownTaskKeys.has(userTaskKey));

	if (newTask !== undefined) {
		return {type: 'task', userTaskKey: newTask.userTaskKey};
	}

	const knownInstanceKeys = new Set(activity.knownElementInstanceKeys);
	const hasNewInstance = elementInstances.some(
		({elementId, elementInstanceKey}) => elementId === activity.elementId && !knownInstanceKeys.has(elementInstanceKey),
	);

	return hasNewInstance && !activity.expectsUserTask ? {type: 'started'} : {type: 'pending'};
}

/**
 * The case has moved on when the closed stage is no longer active, and another stage is active or the case
 * ended. The user task search must also know every active user task. The searches are eventually
 * consistent, and each one can trail the others.
 */
function hasCaseMovedOn({elementInstances, userTasks}: CaseSnapshot, stage: StageCompletionRequest): boolean {
	const isStageClosed = elementInstances.some(
		({elementInstanceKey, state}) => elementInstanceKey === stage.stageInstanceKey && state !== 'ACTIVE',
	);
	const hasNextStage = elementInstances.some(
		({elementId, elementInstanceKey, state}) =>
			state === 'ACTIVE' && stage.stageIds.includes(elementId) && elementInstanceKey !== stage.stageInstanceKey,
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

const activateActivityLogic = fromPromise<void, ActivityRequest>(async ({input}) =>
	activateElement(input.stageInstanceKey, input.elementId),
);

/**
 * Completes a stage with the manual completion pattern of STAGES.md: activate "I am done", then let
 * "Stage done" catch the message.
 *
 * prototype: in the design, "I am done" publishes the message through a job worker. The case page
 * correlates the message itself, so that the demo runs without a worker.
 */
const completeStageLogic = fromPromise<void, {stage: StageCompletionRequest; tenantId: string}>(async ({input}) => {
	const {stageInstanceKey, completion} = input.stage;

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

/** Runs one action of the case worker at a time, and waits until the searches show its result. */
const caseWorkMachine = setup({
	types: {
		context: {} as MachineContext,
		input: {} as MachineInput,
		events: {} as CaseWorkEvent,
		emitted: {} as {type: 'task.opened'; userTaskKey: string},
		tags: {} as CaseWorkTag,
	},
	actors: {
		activateActivity: activateActivityLogic,
		completeStage: completeStageLogic,
		fetchCase: fetchCaseLogic,
	},
	guards: {
		hasNewTask: ({context}, params: {snapshot: CaseSnapshot}) =>
			context.activity !== null && getActivityOutcome(params.snapshot, context.activity).type === 'task',
		hasActivityStarted: ({context}, params: {snapshot: CaseSnapshot}) =>
			context.activity !== null && getActivityOutcome(params.snapshot, context.activity).type === 'started',
		hasCaseMovedOn: ({context}, params: {snapshot: CaseSnapshot}) =>
			context.stage !== null && hasCaseMovedOn(params.snapshot, context.stage),
		hasWaitedTooLong: ({context}) => context.pollCount >= MAX_POLLS,
	},
	actions: {
		storeActivity: assign({
			activity: ({event}) => {
				if (event.type !== 'activity.open') {
					return null;
				}

				const {type: _, ...activity} = event;
				return activity;
			},
			pollCount: 0,
		}),
		storeStage: assign({
			stage: ({event}) => {
				if (event.type !== 'stage.complete') {
					return null;
				}

				const {type: _, ...stage} = event;
				return stage;
			},
			pollCount: 0,
		}),
		incrementPollCount: assign({pollCount: ({context}) => context.pollCount + 1}),
		clearRequests: assign({activity: null, stage: null}),
		openTask: emit(({context}, params: {snapshot: CaseSnapshot}) => {
			const outcome = context.activity === null ? null : getActivityOutcome(params.snapshot, context.activity);

			return {type: 'task.opened' as const, userTaskKey: outcome?.type === 'task' ? outcome.userTaskKey : ''};
		}),
		refreshCase: ({context}) => {
			const {queryClient} = context;

			void queryClient.invalidateQueries({queryKey: ['processInstance']});
			void queryClient.invalidateQueries({queryKey: ['queryVariables']});
			void queryClient.invalidateQueries({queryKey: ['queryElementInstances']});
			void queryClient.invalidateQueries({queryKey: ['userTasks']});
		},
		notifyActivityFailure: () => {
			toast.error(t('tasklist.caseActivityStartFailed'));
		},
		notifyStageFailure: () => {
			toast.error(t('tasklist.caseStageDoneFailed'));
		},
		notifySlowActivity: () => {
			toast.info(t('tasklist.caseActivitySlow'));
		},
		notifySlowStage: () => {
			toast.info(t('tasklist.caseStageDoneSlow'));
		},
	},
	delays: {
		POLL_DELAY: ({context}) => Math.min(500 * 2 ** context.pollCount, 2000),
	},
}).createMachine({
	id: 'caseWork',
	context: ({input}) => ({...input, activity: null, stage: null, pollCount: 0}),
	initial: 'Idle',
	states: {
		Idle: {
			on: {
				'activity.open': {target: 'OpeningActivity', actions: 'storeActivity'},
				'stage.complete': {target: 'CompletingStage', actions: 'storeStage'},
			},
		},

		OpeningActivity: {
			tags: 'status:opening_activity',
			initial: 'Activating',
			states: {
				Activating: {
					invoke: {
						src: 'activateActivity',
						input: ({context}) => context.activity!,
						onDone: {target: 'Fetching'},
						onError: {
							target: '#caseWork.Idle',
							actions: ['notifyActivityFailure', 'clearRequests', 'refreshCase'],
						},
					},
				},
				Fetching: {
					invoke: {
						src: 'fetchCase',
						input: ({context}) => ({queryClient: context.queryClient, caseRef: context.caseRef}),
						onDone: [
							{
								guard: {type: 'hasNewTask', params: ({event}) => ({snapshot: event.output})},
								target: '#caseWork.Idle',
								actions: [
									{type: 'openTask', params: ({event}) => ({snapshot: event.output})},
									'clearRequests',
									'refreshCase',
								],
							},
							{
								guard: {type: 'hasActivityStarted', params: ({event}) => ({snapshot: event.output})},
								target: '#caseWork.Idle',
								actions: ['clearRequests', 'refreshCase'],
							},
							{target: 'Waiting', actions: 'incrementPollCount'},
						],
						onError: {target: 'Waiting', actions: 'incrementPollCount'},
					},
				},
				Waiting: {
					always: {
						guard: 'hasWaitedTooLong',
						target: '#caseWork.Idle',
						actions: ['notifySlowActivity', 'clearRequests', 'refreshCase'],
					},
					after: {POLL_DELAY: {target: 'Fetching'}},
				},
			},
		},

		CompletingStage: {
			tags: 'status:completing_stage',
			invoke: {
				src: 'completeStage',
				input: ({context}) => ({stage: context.stage!, tenantId: context.caseRef.tenantId}),
				onDone: {target: 'WaitingForNextStage'},
				onError: {target: 'Idle', actions: ['notifyStageFailure', 'clearRequests', 'refreshCase']},
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
								target: '#caseWork.Idle',
								actions: ['clearRequests', 'refreshCase'],
							},
							{target: 'Waiting', actions: 'incrementPollCount'},
						],
						onError: {target: 'Waiting', actions: 'incrementPollCount'},
					},
				},
				Waiting: {
					always: {
						guard: 'hasWaitedTooLong',
						target: '#caseWork.Idle',
						actions: ['notifySlowStage', 'clearRequests', 'refreshCase'],
					},
					after: {POLL_DELAY: {target: 'Fetching'}},
				},
			},
		},
	},
});

export {caseWorkMachine};
export type {ActivityRequest, StageCompletionRequest};
