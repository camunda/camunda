/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {TaskDetailsLayout} from '#/tasklist/modules/task-details/components/TaskDetailsLayout';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	refetch: () => void;
	children: React.ReactNode;
};

const TaskDetailPage: React.FC<Props> = ({task, currentUser, children}) => {
	return (
		<TaskDetailsLayout task={task} currentUser={currentUser} assignButton={null}>
			{children}
		</TaskDetailsLayout>
	);
};

export {TaskDetailPage};
