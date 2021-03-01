/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery, useLazyQuery} from '@apollo/client';
import {NetworkStatus} from '@apollo/client';
import {useLocation} from 'react-router-dom';

import {
  GET_TASKS,
  GetTasks,
  GetTasksVariables,
} from 'modules/queries/get-tasks';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
} from 'modules/queries/get-current-user';
import {MAX_TASKS_PER_REQUEST} from 'modules/constants/tasks';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {FilterValues} from 'modules/constants/filterValues';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {useEffect} from 'react';

function useTasks() {
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;
  const isClaimedByMeFilter = filter === FilterValues.ClaimedByMe;
  const currentUserResult = useQuery<GetCurrentUser>(GET_CURRENT_USER, {
    skip: !isClaimedByMeFilter,
  });

  const [fetchTasks, {data, networkStatus, fetchMore, called}] = useLazyQuery<
    GetTasks,
    GetTasksVariables
  >(GET_TASKS, {
    variables: getQueryVariables(filter, {
      username: currentUserResult.data?.currentUser.username,
      pageSize: MAX_TASKS_PER_REQUEST,
    }),
    fetchPolicy: 'network-only',
    nextFetchPolicy: 'cache-first',
    notifyOnNetworkStatusChange: true,
  });
  const tasks = data?.tasks ?? [];

  const fetchPreviousTasks = async () => {
    if (fetchMore === undefined) {
      return [];
    }

    try {
      const {
        data: {tasks: latestFetchedTasks},
      } = await fetchMore({
        variables: {searchBefore: tasks[0]?.sortValues},
      });

      return latestFetchedTasks;
    } catch (error) {
      console.error(error);
      return [];
    }
  };

  const fetchNextTasks = async () => {
    if (fetchMore === undefined) {
      return;
    }

    try {
      await fetchMore({
        variables: {
          searchAfter: tasks[tasks.length - 1]?.sortValues,
        },
      });
    } catch (error) {
      console.error(error);
    }
  };

  const shouldFetchMoreTasks =
    fetchMore !== undefined && networkStatus === NetworkStatus.ready;

  useEffect(() => {
    if (currentUserResult.data === undefined && isClaimedByMeFilter) {
      return;
    }
    fetchTasks();
  }, [fetchTasks, currentUserResult, isClaimedByMeFilter]);

  return {
    tasks,
    networkStatus,
    shouldFetchMoreTasks,
    fetchPreviousTasks,
    fetchNextTasks,
    loading:
      !called ||
      networkStatus === NetworkStatus.loading ||
      networkStatus === NetworkStatus.setVariables ||
      currentUserResult.loading,
    isFirstLoad:
      !called ||
      networkStatus === NetworkStatus.loading ||
      currentUserResult.networkStatus === NetworkStatus.loading,
  };
}

export {useTasks};
