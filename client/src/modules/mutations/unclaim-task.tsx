/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {unclaimedTask} from 'modules/mock-schema/mocks/task';

const UNCLAIM_TASK =
  process.env.NODE_ENV === 'test'
    ? gql`
        mutation UnclaimTask($id: ID!) {
          unclaimTask(id: $id) {
            id
          }
        }
      `
    : gql`
        mutation UnclaimTask($id: ID!) {
          unclaimTask(id: $id) @client {
            id
          }
        }
      `;

const mockUnclaimTask = {
  request: {
    query: UNCLAIM_TASK,
    variables: {id: '1'},
  },
  result: {
    data: {
      unclaimTask: unclaimedTask,
    },
  },
};

export {UNCLAIM_TASK, mockUnclaimTask};
