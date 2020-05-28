/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';

const tasks: ReadonlyArray<Task> = [
  {
    key: '0',
    name: 'name',
    worflowName: 'worflowName',
    creationTime: new Date().toISOString(),
    completionTime: new Date().toISOString(),
    assignee: {
      username: 'Demo',
      firstname: 'Demo',
      lastname: 'user',
    },
    variables: [],
    taskState: 'COMPLETED',
  },
  {
    key: '1',
    name: 'name',
    worflowName: 'worflowName',
    creationTime: new Date().toISOString(),
    completionTime: new Date().toISOString(),
    assignee: {
      username: 'mustermann',
      firstname: 'Otto',
      lastname: 'Mustermann',
    },
    variables: [],
    taskState: 'CREATED',
  },
];

export {tasks};
