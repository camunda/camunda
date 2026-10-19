/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {createFileRoute, useNavigate} from '@tanstack/react-router';
import {TasklistCaseTasksPage} from '#/tasklist/pages/TasklistCaseTasksPage';
import {useCaseData} from '#/tasklist/modules/case-details/caseData';

export const Route = createFileRoute('/_shadcn/_auth/tasklist/cases/$processInstanceKey/')({
	component: function TasklistCaseTasksRoute() {
		const navigate = useNavigate();
		const {processInstanceKey} = Route.useParams();
		const caseData = useCaseData(processInstanceKey);
		const openTask = useCallback(
			(userTaskKey: string) =>
				navigate({
					to: '/tasklist/cases/$processInstanceKey/tasks/$userTaskKey',
					params: {processInstanceKey, userTaskKey},
				}),
			[navigate, processInstanceKey],
		);

		return <TasklistCaseTasksPage {...caseData} onOpenTask={openTask} />;
	},
});
