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
	assignButton: React.ReactNode;
	children: React.ReactNode;
};

const TaskDetailPage: React.FC<Props> = ({task, currentUser, assignButton, children}) => {
	return (
		<TaskDetailsLayout task={task} currentUser={currentUser} assignButton={assignButton}>
			{children}
		</TaskDetailsLayout>
	);
};

export {TaskDetailPage};
