/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';

import {Task} from 'modules/types';

const GET_TASKS = gql`
  query GetTasks {
    tasks @client {
      key
      name
      assignee
    }
  }
`;

interface GetTasks {
  tasks: ReadonlyArray<{
    key: Task['key'];
    name: Task['name'];
    assignee: Task['assignee'];
  }>;
}

export type {GetTasks};
export {GET_TASKS};
