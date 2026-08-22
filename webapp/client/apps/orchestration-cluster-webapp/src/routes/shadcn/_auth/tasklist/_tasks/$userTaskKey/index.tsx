/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute} from '@tanstack/react-router';
import {TaskDetailsPlaceholderPage} from '#/tasklist/pages/shadcn.components/TaskDetailsPlaceholderPage';

export const Route = createFileRoute('/shadcn/_auth/tasklist/_tasks/$userTaskKey/')({
	component: function TaskDetailsPlaceholderRoute() {
		const {userTaskKey} = Route.useParams();

		return <TaskDetailsPlaceholderPage userTaskKey={userTaskKey} />;
	},
});
