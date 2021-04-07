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
  unclaimedTaskWithForm,
  unclaimedTaskWithPrefilledForm,
  claimedTaskWithForm,
  claimedTaskWithPrefilledForm,
  completedTaskWithForm,
} from 'modules/mock-schema/mocks/task';

type TaskQueryVariables = Pick<Task, 'id'>;

interface GetTask {
  task: Pick<
    Task,
    | 'id'
    | 'assignee'
    | 'name'
    | 'taskState'
    | 'processName'
    | 'creationTime'
    | 'completionTime'
    | 'variables'
    | 'formKey'
    | 'processDefinitionId'
  >;
}

const GET_TASK = gql`
  query GetTask($id: String!) {
    task(id: $id) {
      id
      formKey
      processDefinitionId
      assignee {
        username
        firstname
        lastname
      }
      name
      taskState
      processName
      creationTime
      completionTime
      variables {
        name
        value
      }
    }
  }
`;

const mockGetTaskUnclaimed = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: unclaimedTask(id),
    },
  },
});

const mockGetTaskUnclaimedWithForm = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: unclaimedTaskWithForm(id),
    },
  },
});

const mockGetTaskUnclaimedWithPrefilledForm = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: unclaimedTaskWithPrefilledForm(id),
    },
  },
});

const mockGetTaskCompleted = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: completedTask(id),
    },
  },
});

const mockGetTaskClaimed = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: claimedTask(id),
    },
  },
});

const mockGetTaskClaimedWithForm = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: claimedTaskWithForm(id),
    },
  },
});

const mockGetTaskClaimedWithPrefilledForm = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: claimedTaskWithPrefilledForm(id),
    },
  },
});

const mockGetTaskUnclaimedWithVariables = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: unclaimedTaskWithVariables(id),
    },
  },
});

const mockGetTaskCompletedWithVariables = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: completedTaskWithVariables(id),
    },
  },
});

const mockGetTaskClaimedWithVariables = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: claimedTaskWithVariables(id),
    },
  },
});

const mockGetTaskCompletedWithForm = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: completedTaskWithForm(id),
    },
  },
});

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
  mockGetTaskUnclaimedWithForm,
  mockGetTaskUnclaimedWithPrefilledForm,
  mockGetTaskClaimedWithForm,
  mockGetTaskClaimedWithPrefilledForm,
  mockGetTaskCompletedWithForm,
  useTask,
};
