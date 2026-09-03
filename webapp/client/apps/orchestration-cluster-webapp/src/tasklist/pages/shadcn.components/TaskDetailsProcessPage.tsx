/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {ProcessDiagramView} from '#/tasklist/modules/task-details/shadcn.components/process-diagram/ProcessDiagramView';

type Props = {
	task: UserTask;
	processXml: string;
};

const TaskDetailsProcessPage: React.FC<Props> = ({task, processXml}) => {
	return (
		<div className="flex h-full min-h-0 w-full" data-testid="process-tab-content">
			<ProcessDiagramView
				xml={processXml}
				elementId={task.elementId}
				processName={task.processName ?? task.processDefinitionId}
				processVersion={task.processDefinitionVersion}
			/>
		</div>
	);
};

export {TaskDetailsProcessPage};
