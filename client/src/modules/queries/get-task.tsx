/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {gql, useApolloClient, useQuery} from '@apollo/client';
import {Task} from 'modules/types';

import {
  unclaimedTask,
  completedTask,
  claimedTask,
  unclaimedTaskWithForm,
  claimedTaskWithForm,
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
      assignee
      name
      taskState
      processName
      creationTime
      completionTime
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

function useRemoveFormReference(task: GetTask['task']) {
  const client = useApolloClient();

  function removeFormReference() {
    client.writeQuery({
      query: GET_TASK,
      data: {
        task: {
          ...task,
          formKey: null,
          processDefinitionId: null,
        },
      },
      variables: {
        id: task.id,
      },
    });
  }

  return {removeFormReference};
}

export type {GetTask, TaskQueryVariables};
export {
  GET_TASK,
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskClaimed,
  mockGetTaskUnclaimedWithForm,
  mockGetTaskClaimedWithForm,
  mockGetTaskCompletedWithForm,
  useTask,
  useRemoveFormReference,
};
