/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {CaseTaskPanel} from '#/tasklist/modules/case-details/components/CaseTaskPanel';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	formSchema: string | null;
	variables: Variable[];
	onCompleted: () => void;
	onClose: () => void;
};

const TasklistCaseTaskPage: React.FC<Props> = ({task, currentUser, formSchema, variables, onCompleted, onClose}) => (
	<CaseTaskPanel
		key={task.userTaskKey}
		task={task}
		currentUser={currentUser}
		formSchema={formSchema}
		variables={variables}
		onCompleted={onCompleted}
		onClose={onClose}
	/>
);

export {TasklistCaseTaskPage};
