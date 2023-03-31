/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {gql, useApolloClient, useQuery} from '@apollo/client';
import {Task} from 'modules/types';

import {
  unassignedTask,
  completedTask,
  assignedTask,
  unassignedTaskWithForm,
  assignedTaskWithForm,
  completedTaskWithForm,
} from 'modules/mock-schema/mocks/task';
import {useEffect, useState} from 'react';

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
    | 'dueDate'
    | 'followUpDate'
    | 'candidateGroups'
    | 'candidateUsers'
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
      candidateGroups
      candidateUsers
      dueDate
      followUpDate
    }
  }
`;

const mockGetTaskUnassigned = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: unassignedTask(id),
    },
  },
});

const mockGetTaskUnassignedWithForm = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: unassignedTaskWithForm(id),
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

const mockGetTaskAssigned = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: assignedTask(id),
    },
  },
});

const mockGetTaskAssignedWithForm = (id = '0') => ({
  request: {
    query: GET_TASK,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: assignedTaskWithForm(id),
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
  const [usePreviousData, setUsePrevious] = useState(true);
  const result = useQuery<GetTask, TaskQueryVariables>(GET_TASK, {
    variables: {id},
  });

  useEffect(() => {
    const {data, previousData} = result;
    let timeoutId: ReturnType<typeof setTimeout>;

    if (data === undefined && previousData !== undefined && usePreviousData) {
      timeoutId = setTimeout(() => {
        setUsePrevious(false);
      }, 500);
    }

    if (data !== undefined) {
      setUsePrevious(true);
      clearTimeout(timeoutId!);
    }

    return () => {
      clearTimeout(timeoutId);
    };
  }, [usePreviousData, result]);

  if (result.data !== undefined) {
    return result;
  }

  if (usePreviousData && result.previousData !== undefined) {
    return {
      ...result,
      loading: false,
      data: result.previousData,
    };
  }

  return result;
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
  mockGetTaskUnassigned,
  mockGetTaskCompleted,
  mockGetTaskAssigned,
  mockGetTaskUnassignedWithForm,
  mockGetTaskAssignedWithForm,
  mockGetTaskCompletedWithForm,
  useTask,
  useRemoveFormReference,
};
