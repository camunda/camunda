/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {ProcessInstance, ProcessInstancePending} from '#/operate/pages/ProcessInstance/ProcessInstance';
import {processInstanceQuery} from '#/operate/pages/ProcessInstance/processInstance.queries';
import {processInstanceSearchSchema} from '#/operate/pages/ProcessInstance/processInstanceSearch';

const Route = createFileRoute('/_carbon/_auth/operate/processes_/$processInstanceId')({
	validateSearch: processInstanceSearchSchema,
	loader: ({context: {queryClient}, params: {processInstanceId}}) => {
		void queryClient.prefetchQuery(processInstanceQuery(processInstanceId));
	},
	pendingComponent: ProcessInstancePending,
	component: function ProcessInstanceRoute() {
		const {processInstanceId} = Route.useParams();
		return <ProcessInstance processInstanceId={processInstanceId} search={Route.useSearch()} />;
	},
});

export {Route};
