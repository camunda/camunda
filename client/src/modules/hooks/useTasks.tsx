/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery, useLazyQuery, NetworkStatus} from '@apollo/client';
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
import {
  MAX_TASKS_PER_REQUEST,
  MAX_TASKS_DISPLAYED,
} from 'modules/constants/tasks';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {FilterValues} from 'modules/constants/filterValues';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {useEffect, useRef} from 'react';
import {getSortValues} from 'modules/utils/getSortValues';

function useTasks({withPolling}: {withPolling?: boolean}) {
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;
  const isClaimedByMeFilter = filter === FilterValues.ClaimedByMe;
  const currentUserResult = useQuery<GetCurrentUser>(GET_CURRENT_USER, {
    skip: !isClaimedByMeFilter,
  });

  let timeout = useRef<NodeJS.Timeout | undefined>();

  const [fetchTasks, {data, networkStatus, fetchMore, called}] = useLazyQuery<
    GetTasks,
    GetTasksVariables
  >(GET_TASKS, {
    variables: getQueryVariables(filter, {
      userId: currentUserResult.data?.currentUser.userId,
      pageSize: MAX_TASKS_PER_REQUEST,
    }),
    fetchPolicy: 'network-only',
    nextFetchPolicy: 'cache-first',
    notifyOnNetworkStatusChange: true,
    context: {
      headers: withPolling
        ? {
            'x-is-polling': 'true',
          }
        : undefined,
    },
  });

  const tasks = data?.tasks;
  const fetchPreviousTasks = async () => {
    if (fetchMore === undefined) {
      return [];
    }

    try {
      const {
        data: {tasks: latestFetchedTasks},
      } = await fetchMore({
        variables: {
          searchBefore: tasks?.[0]?.sortValues,
          pageSize: MAX_TASKS_PER_REQUEST,
        },
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
          searchAfter: tasks?.[tasks.length - 1]?.sortValues,
          pageSize: MAX_TASKS_PER_REQUEST,
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

  useEffect(() => {
    if (
      withPolling &&
      networkStatus === NetworkStatus.ready &&
      timeout.current === undefined
    ) {
      timeout.current = setTimeout(() => {
        if (fetchMore !== undefined && timeout.current !== undefined) {
          const taskLength = tasks?.length ?? 0;

          fetchMore({
            variables: {
              pageSize:
                taskLength <= MAX_TASKS_PER_REQUEST
                  ? MAX_TASKS_PER_REQUEST
                  : MAX_TASKS_DISPLAYED,
              searchAfterOrEqual: getSortValues(tasks),
              isPolling: true,
            },
          });
        }
      }, 5000);
    }

    return () => {
      if (withPolling && timeout.current !== undefined) {
        clearTimeout(timeout.current);
        timeout.current = undefined;
      }
    };
  }, [withPolling, networkStatus, tasks, fetchMore]);

  return {
    tasks: tasks ?? [],
    networkStatus,
    shouldFetchMoreTasks,
    fetchPreviousTasks,
    fetchNextTasks,
    loading:
      !called ||
      networkStatus === NetworkStatus.loading ||
      networkStatus === NetworkStatus.setVariables ||
      currentUserResult.loading,
  };
}

export {useTasks};
