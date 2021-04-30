/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql, useQuery} from '@apollo/client';
import {Task} from 'modules/types';

type TaskVariablesQueryVariables = Pick<Task, 'id'>;

interface GetTaskVariables {
  task: Pick<Task, 'id' | 'variables'>;
}

const GET_TASK_VARIABLES = gql`
  query GetTaskVariables($id: String!) {
    task(id: $id) {
      id
      variables {
        name
        value
      }
    }
  }
`;

const mockGetTaskVariables = (id = '0') => ({
  request: {
    query: GET_TASK_VARIABLES,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: {
        __typename: 'Task',
        id,
        variables: [
          {
            name: 'myVar',
            value: '"0001"',
            __typename: 'Variable',
          },
          {
            name: 'isCool',
            value: '"yes"',
            __typename: 'Variable',
          },
        ],
      },
    },
  },
});

const mockGetTaskEmptyVariables = (id = '0') => ({
  request: {
    query: GET_TASK_VARIABLES,
    variables: {
      id,
    },
  },
  result: {
    data: {
      task: {
        __typename: 'Task',
        id,
        variables: [],
      },
    },
  },
});

function useTaskVariables(id: Task['id']) {
  const {data, loading} = useQuery<
    GetTaskVariables,
    TaskVariablesQueryVariables
  >(GET_TASK_VARIABLES, {
    variables: {
      id,
    },
  });

  return {
    variables: data?.task?.variables ?? [],
    loading: loading || data?.task?.variables === undefined,
  };
}

export {useTaskVariables, mockGetTaskVariables, mockGetTaskEmptyVariables};
