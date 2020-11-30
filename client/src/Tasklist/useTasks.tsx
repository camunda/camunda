/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery} from '@apollo/client';
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
import {getSearchParam} from 'modules/utils/getSearchParam';
import {FilterValues} from 'modules/constants/filterValues';
import {getQueryVariables} from 'modules/utils/getQueryVariables';

function useTasks() {
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;
  const isClaimedByMeFilter = filter === FilterValues.ClaimedByMe;
  const currentUserResult = useQuery<GetCurrentUser>(GET_CURRENT_USER, {
    skip: !isClaimedByMeFilter,
  });
  const {data, loading, networkStatus} = useQuery<GetTasks, GetTasksVariables>(
    GET_TASKS,
    {
      skip: currentUserResult.data === undefined && isClaimedByMeFilter,
      variables: getQueryVariables(filter, {
        username: currentUserResult.data?.currentUser.username,
      }),
      fetchPolicy: 'network-only',
    },
  );

  const tasks = data?.tasks ?? [];

  return {
    tasks,
    loading: loading || currentUserResult.loading,
    isFirstLoad:
      networkStatus === NetworkStatus.loading ||
      currentUserResult.networkStatus === NetworkStatus.loading,
  };
}

export {useTasks};
