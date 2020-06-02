/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';

import {Task} from 'modules/types';

const GET_TASK = gql`
  query GetTask($key: ID!) {
    task(key: $key) @client {
      key
      name
      assignee
    }
  }
`;

interface GetTask {
  task: {
    key: Task['key'];
    name: Task['name'];
    assignee: Task['assignee'];
  };
}

interface GetTaskVariables {
  key: Task['key'];
}

export type {GetTask, GetTaskVariables};
export {GET_TASK};
