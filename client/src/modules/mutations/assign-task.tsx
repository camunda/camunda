/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {GraphqlTask} from 'modules/types';
import {assignedTask} from 'modules/mock-schema/mocks/task';
import {convertToGraphqlTask} from 'modules/utils/convertToGraphqlTask';

type AssignTaskVariables = {
  id: GraphqlTask['id'];
};

const ASSIGN_TASK = gql`
  mutation ClaimTask($id: String!) {
    claimTask(taskId: $id) {
      id
      assignee
    }
  }
`;

const mockAssignTask = {
  request: {
    query: ASSIGN_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      claimTask: convertToGraphqlTask(assignedTask()),
    },
  },
};

export type {AssignTaskVariables};
export {ASSIGN_TASK, mockAssignTask};
