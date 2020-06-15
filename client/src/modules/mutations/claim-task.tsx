/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {claimedTask} from 'modules/mock-schema/mocks/task';

const CLAIM_TASK =
  process.env.NODE_ENV === 'test'
    ? gql`
        mutation ClaimTask($key: ID!) {
          claimTask(key: $key) {
            key
          }
        }
      `
    : gql`
        mutation ClaimTask($key: ID!) {
          claimTask(key: $key) @client {
            key
          }
        }
      `;

const mockClaimTask = {
  request: {
    query: CLAIM_TASK,
    variables: {key: '1'},
  },
  result: {
    data: {
      claimTask: claimedTask,
    },
  },
};

export {CLAIM_TASK, mockClaimTask};
