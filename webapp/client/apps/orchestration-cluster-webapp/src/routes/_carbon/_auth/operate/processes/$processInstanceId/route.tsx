/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, Outlet} from '@tanstack/react-router';
import {t} from 'i18next';
import {ProcessInstance, ProcessInstancePending} from '#/operate/pages/ProcessInstance/ProcessInstance';
import {InstanceDiagram} from '#/operate/pages/ProcessInstance/InstanceDiagram';
import {processInstanceQuery} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {validateProcessInstanceRouteSearch} from '#/operate/pages/ProcessInstance/processInstanceSearch';
import {getProcessDefinitionName} from '#/operate/shared/utils/processInstance';
import {ForbiddenError} from '#/shared/errors';
import {requestErrorSchema} from '#/shared/http/request';

const Route = createFileRoute('/_carbon/_auth/operate/processes/$processInstanceId')({
	validateSearch: validateProcessInstanceRouteSearch,
	loader: async ({context: {queryClient}, params: {processInstanceId}}) => {
		try {
			return await queryClient.ensureQueryData(processInstanceQuery(processInstanceId));
		} catch (error) {
			if (!(error instanceof ForbiddenError) && !requestErrorSchema.safeParse(error).success) {
				throw error;
			}
			return undefined;
		}
	},
	head: ({loaderData, params: {processInstanceId}}) => ({
		meta: loaderData
			? [
					{
						title: t('operate.processInstance.pageTitle', {
							processInstanceId,
							name: getProcessDefinitionName(loaderData),
						}),
					},
				]
			: [],
	}),
	pendingComponent: ProcessInstancePending,
	component: function ProcessInstanceRoute() {
		const {processInstanceId} = Route.useParams();
		return (
			<ProcessInstance
				processInstanceId={processInstanceId}
				search={Route.useSearch()}
				topPanel={<InstanceDiagram />}
				bottomPanel={<Outlet />}
			/>
		);
	},
});

export {Route};
