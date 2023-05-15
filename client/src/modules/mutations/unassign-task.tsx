/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {unassignedTask} from 'modules/mock-schema/mocks/task';
import {GraphqlTask} from 'modules/types';
import {convertToGraphqlTask} from 'modules/utils/convertToGraphqlTask';

type UnassignTaskVariables = {
  id: GraphqlTask['id'];
};

const UNASSIGN_TASK = gql`
  mutation UnclaimTask($id: String!) {
    unclaimTask(taskId: $id) {
      id
      assignee
    }
  }
`;

const mockUnassignTask = {
  request: {
    query: UNASSIGN_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      unclaimTask: convertToGraphqlTask(unassignedTask()),
    },
  },
};

export type {UnassignTaskVariables};
export {UNASSIGN_TASK, mockUnassignTask};
