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

export {useActivateActivity};
