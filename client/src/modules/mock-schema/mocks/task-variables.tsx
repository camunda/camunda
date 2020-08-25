/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';

type TaskVariables = Pick<Task, 'id' | 'variables' | '__typename'>;

const taskWithVariables: TaskVariables = {
  __typename: 'Task',
  id: '0',
  variables: [
    {name: 'myVar', value: '"0001"'},
    {name: 'isCool', value: '"yes"'},
  ],
};

const taskWithoutVariables: TaskVariables = {
  __typename: 'Task',
  id: '0',
  variables: [],
};

export {taskWithVariables, taskWithoutVariables};
