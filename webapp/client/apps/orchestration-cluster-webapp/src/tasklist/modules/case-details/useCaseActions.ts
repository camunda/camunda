/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {endpoints} from '#/shared/http/endpoints';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {request} from '#/shared/http/request';
import type {StageCompletion} from '#/tasklist/modules/case-details/getStageWork';

async function activateElement(adHocSubProcessInstanceKey: string, elementId: string): Promise<void> {
	const {error} = await request(
		endpoints.activateAdHocSubProcessActivities({adHocSubProcessInstanceKey, elements: [{elementId}]}),
	);

	if (error !== null) {
		throw mapQueryError(error);
	}
}

function useInvalidateCase() {
	const queryClient = useQueryClient();

	return () =>
		Promise.all([
			queryClient.invalidateQueries({queryKey: ['queryElementInstances']}),
			queryClient.invalidateQueries({queryKey: ['userTasks']}),
			queryClient.invalidateQueries({queryKey: ['processInstance']}),
		]);
}

function useActivateActivity(adHocSubProcessInstanceKey: string | null) {
	const invalidateCase = useInvalidateCase();

	return useMutation({
		mutationFn: async (elementId: string) => {
			if (adHocSubProcessInstanceKey === null) {
				throw new Error('The stage has no active element instance');
			}

			await activateElement(adHocSubProcessInstanceKey, elementId);
		},
		onSettled: invalidateCase,
	});
}

/**
 * Completes a stage with the manual completion pattern of STAGES.md: activate "I am done", then let
 * "Stage done" catch the message.
 *
 * prototype: in the design, "I am done" publishes the message through a job worker. The case page
 * correlates the message itself, so that the demo runs without a worker.
 */
function useCompleteStage(adHocSubProcessInstanceKey: string | null, tenantId: string) {
	const invalidateCase = useInvalidateCase();

	return useMutation({
		mutationFn: async ({messageName, doneElementId, correlationKey}: StageCompletion) => {
			if (correlationKey === null) {
				throw new Error('The correlation key of the stage cannot be resolved');
			}

			if (doneElementId !== null && adHocSubProcessInstanceKey !== null) {
				await activateElement(adHocSubProcessInstanceKey, doneElementId);
			}

			const {error} = await request(endpoints.correlateMessage({name: messageName, correlationKey, tenantId}));

			if (error !== null) {
				throw mapQueryError(error);
			}
		},
		onSettled: invalidateCase,
	});
}

export {useActivateActivity, useCompleteStage};
