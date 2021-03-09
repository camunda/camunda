/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql, useQuery} from '@apollo/client';
import {Task} from 'modules/types';

import {
  unclaimedTask,
  completedTask,
  claimedTask,
  unclaimedTaskWithVariables,
  completedTaskWithVariables,
  claimedTaskWithVariables,
} from 'modules/mock-schema/mocks/task';

type TaskQueryVariables = Pick<Task, 'id'>;

interface GetTask {
  task: Pick<
    Task,
    | 'id'
    | 'assignee'
    | 'name'
    | 'taskState'
    | 'workflowName'
    | 'creationTime'
    | 'completionTime'
    | 'variables'
  >;
}

const GET_TASK = gql`
  query GetTask($id: String!) {
    task(id: $id) {
      id
      assignee {
        username
        firstname
        lastname
      }
      name
      taskState
      workflowName
      creationTime
      completionTime
      variables {
        name
        value
      }
    }
  }
`;

const mockGetTaskUnclaimed = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: unclaimedTask,
    },
  },
};

const mockGetTaskCompleted = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: completedTask,
    },
  },
};

const mockGetTaskClaimed = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: claimedTask,
    },
  },
};

const mockGetTaskUnclaimedWithVariables = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: unclaimedTaskWithVariables,
    },
  },
};

const mockGetTaskCompletedWithVariables = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: completedTaskWithVariables,
    },
  },
};

const mockGetTaskClaimedWithVariables = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: claimedTaskWithVariables,
    },
  },
};

function useTask(id: Task['id']) {
  const result = useQuery<GetTask, TaskQueryVariables>(GET_TASK, {
    variables: {id},
  });

  return {
    ...result,
    data: result.data ?? result.previousData,
  };
}

export type {GetTask, TaskQueryVariables};
export {
  GET_TASK,
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskClaimed,
  mockGetTaskUnclaimedWithVariables,
  mockGetTaskCompletedWithVariables,
  mockGetTaskClaimedWithVariables,
  useTask,
};
