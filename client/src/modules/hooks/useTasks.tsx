/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {NetworkStatus, useQuery, FetchMoreOptions} from '@apollo/client';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {
  GET_TASKS,
  GetTasks,
  GetTasksVariables,
} from 'modules/queries/get-tasks';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {useEffect, useState} from 'react';
import {useTaskFilters} from './useTaskFilters';

const POLLING_INTERVAL = 5000;
const MAX_TASKS_DISPLAYED = 200;
const MAX_TASKS_PER_REQUEST = 50;

type UpdateQuery = FetchMoreOptions<GetTasks, GetTasksVariables>['updateQuery'];

function useTasks() {
  const filters = useTaskFilters();
  const currentUserResult = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const [variables, setVariables] = useState(
    getQueryVariables(filters, {
      userId: currentUserResult.data?.currentUser.userId,
      pageSize: MAX_TASKS_PER_REQUEST,
    }),
  );

  const {
    data,
    fetchMore,
    stopPolling,
    startPolling,
    client,
    refetch,
    networkStatus,
    previousData,
  } = useQuery<GetTasks, GetTasksVariables>(GET_TASKS, {
    variables,
    pollInterval: POLLING_INTERVAL,
    skip: currentUserResult.data === undefined,
    fetchPolicy: 'network-only',
    nextFetchPolicy: 'cache-and-network',
    context: {
      headers: {
        'x-is-polling': 'true',
      },
    },
  });

  const getUpdateQuery = (
    mergeTasks: (
      currentTasks: GetTasks['tasks'],
      newTasks: GetTasks['tasks'],
    ) => GetTasks['tasks'],
  ): UpdateQuery => {
    return (currentResult, {fetchMoreResult}) => {
      if (!Array.isArray(fetchMoreResult?.tasks)) {
        return currentResult;
      }

      const newTasks = mergeTasks(
        currentResult.tasks ?? [],
        fetchMoreResult?.tasks ?? [],
      );
      const newVariables = getQueryVariables(filters, {
        userId: currentUserResult.data?.currentUser.userId,
        pageSize: newTasks.length,
        searchAfterOrEqual: newTasks[0].sortValues,
      });

      client.cache.writeQuery({
        query: GET_TASKS,
        data: {
          tasks: newTasks,
        },
        variables: newVariables,
      });

      setVariables(newVariables);

      return {
        tasks: newTasks,
      };
    };
  };

  useEffect(() => {
    setVariables(
      getQueryVariables(filters, {
        userId: currentUserResult.data?.currentUser.userId,
        pageSize: MAX_TASKS_PER_REQUEST,
      }),
    );
  }, [filters, currentUserResult.data?.currentUser.userId]);

  const fetchNextTasks = async () => {
    const tasks = data?.tasks;
    if (tasks === undefined) {
      return [];
    }

    if (tasks.length > 0) {
      const lastTask = tasks.at(-1)!;

      stopPolling();

      const newTasks = await fetchMore({
        variables: getQueryVariables(filters, {
          userId: currentUserResult.data?.currentUser.userId,
          pageSize: MAX_TASKS_PER_REQUEST,
          searchAfter: lastTask.sortValues,
        }),
        updateQuery: getUpdateQuery((currentTasks, newTasks) => {
          const mergedTasks = [...currentTasks, ...newTasks];
          return mergedTasks.length > MAX_TASKS_DISPLAYED
            ? mergedTasks.slice(mergedTasks.length - MAX_TASKS_DISPLAYED)
            : mergedTasks;
        }),
      });

      startPolling(POLLING_INTERVAL);

      return newTasks.data?.tasks ?? [];
    }

    return [];
  };

  const fetchPreviousTasks = async () => {
    const tasks = data?.tasks;
    if (tasks === undefined) {
      return [];
    }

    if (tasks.length > 0) {
      const firstTask = tasks[0];

      stopPolling();

      const newTasks = await fetchMore({
        variables: getQueryVariables(filters, {
          userId: currentUserResult.data?.currentUser.userId,
          pageSize: MAX_TASKS_PER_REQUEST,
          searchBefore: firstTask.sortValues,
        }),
        updateQuery: getUpdateQuery((currentTasks, newTasks) => {
          const mergedTasks = [...newTasks, ...currentTasks];
          return mergedTasks.length > MAX_TASKS_DISPLAYED
            ? mergedTasks.slice(0, MAX_TASKS_DISPLAYED)
            : mergedTasks;
        }),
      });

      startPolling(POLLING_INTERVAL);

      return newTasks.data?.tasks ?? [];
    }

    return [];
  };

  return {
    tasks: data?.tasks ?? previousData?.tasks ?? [],
    loading: networkStatus === NetworkStatus.loading,
    fetchPreviousTasks,
    fetchNextTasks,
    refetch: () => {
      refetch(variables);
    },
  };
}

export {useTasks};
