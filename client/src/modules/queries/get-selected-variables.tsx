/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {gql, useQuery} from '@apollo/client';
import {Task, Variable} from 'modules/types';

type SelectedVariablesQueryVariables = {
  taskId: Task['id'];
  variableNames: Variable['name'][];
};

type GetSelectedVariables = {
  variables: Variable[];
};

const GET_SELECTED_VARIABLES = gql`
  query GetSelectedVariables($taskId: String!, $variableNames: [String!]!) {
    variables(taskId: $taskId, variableNames: $variableNames) {
      name
      value
    }
  }
`;

const mockGetSelectedVariables = (taskId = '0') => ({
  request: {
    query: GET_SELECTED_VARIABLES,
    variables: {
      taskId,
      variableNames: ['myVar', 'isCool'],
    },
  },
  result: {
    data: {
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
});

const mockGetSelectedVariablesEmptyVariables = (taskId = '0') => ({
  request: {
    query: GET_SELECTED_VARIABLES,
    variables: {
      taskId,
      variableNames: ['myVar', 'isCool'],
    },
  },
  result: {
    data: {
      variables: [],
    },
  },
});

function useSelectedVariables(
  taskId: Task['id'],
  variableNames: Variable['name'][],
) {
  const {data, loading, client} = useQuery<
    GetSelectedVariables,
    SelectedVariablesQueryVariables
  >(GET_SELECTED_VARIABLES, {
    variables: {
      taskId,
      variableNames,
    },
    skip: variableNames.length === 0,
  });

  function updateSelectedVariables(variables: Variable[]) {
    client.writeQuery({
      query: GET_SELECTED_VARIABLES,
      data: {
        variables: variables.map((variable) => ({
          ...variable,
          __typename: 'Variable',
        })),
      },
      variables: {
        taskId,
        variableNames,
      },
    });
  }

  return {
    variables: data?.variables ?? [],
    loading:
      loading || (data?.variables === undefined && variableNames.length > 0),
    updateSelectedVariables,
  };
}

export {
  useSelectedVariables,
  mockGetSelectedVariables,
  mockGetSelectedVariablesEmptyVariables,
};
