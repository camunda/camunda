/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';

type PartialTask = Pick<Task, 'id' | 'taskState'>;

const taskCreated: PartialTask = {
  id: '0',
  taskState: TaskStates.Created,
};

const taskCompleted: PartialTask = {
  id: '1',
  taskState: TaskStates.Completed,
};

export {taskCreated, taskCompleted};
