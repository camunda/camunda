/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {Task} from 'modules/types';
import {claimedTask} from 'modules/mock-schema/mocks/task';

type ClaimTaskVariables = {
  id: Task['id'];
};

const CLAIM_TASK = gql`
  mutation ClaimTask($id: String!) {
    claimTask(taskId: $id) {
      id
      assignee {
        username
        firstname
        lastname
      }
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
