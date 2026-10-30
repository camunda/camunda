/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, redirect} from '@tanstack/react-router';
import {
	processInstanceQuery,
	processInstanceWaitStateStatisticsQuery,
} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {getDefaultProcessInstanceTab} from '#/operate/pages/ProcessInstance/processInstanceSearch';
import {isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {hasProcessLevelWaitState} from '#/operate/shared/utils/waitStates';

export const Route = createFileRoute('/_shadcn/_auth/operate-preview/processes/$processInstanceId/')({
	beforeLoad: async ({context: {queryClient}, params, search, location}) => {
		const processInstance = await queryClient.ensureQueryData(processInstanceQuery(params.processInstanceId));
		const waitStateStatistics = isInstanceRunning(processInstance)
			? await queryClient.ensureQueryData(processInstanceWaitStateStatisticsQuery(params.processInstanceId))
			: [];
		const tab = getDefaultProcessInstanceTab(processInstance, search, {
			isProcessLevelWaiting: hasProcessLevelWaitState(waitStateStatistics, processInstance.processDefinitionId),
		});
		const redirectOptions = {params, search: location.search, hash: location.hash, replace: true};

		switch (tab) {
			case 'incidents':
				throw redirect({to: '/operate-preview/processes/$processInstanceId/incidents', ...redirectOptions});
			case 'details':
				throw redirect({to: '/operate-preview/processes/$processInstanceId/details', ...redirectOptions});
			case 'variables':
			default:
				throw redirect({to: '/operate-preview/processes/$processInstanceId/variables', ...redirectOptions});
		}
	},
});
