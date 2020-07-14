/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {Task} from 'modules/types';
import {claimedTask} from 'modules/mock-schema/mocks/task-details';

type ClaimTaskVariables = {
  id: Task['id'];
};

const CLAIM_TASK =
  process.env.NODE_ENV === 'test'
    ? gql`
        mutation ClaimTask($id: ID!) {
          claimTask(id: $id) {
            id
          }
        }
      `
    : gql`
        mutation ClaimTask($id: ID!) {
          claimTask(id: $id) @client {
            id
          }
        }
      `;

const mockClaimTask = {
  request: {
    query: CLAIM_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      claimTask: claimedTask,
    },
  },
};

export type {ClaimTaskVariables};
export {CLAIM_TASK, mockClaimTask};
