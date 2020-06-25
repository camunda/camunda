/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {useQuery} from '@apollo/react-hooks';
import {useLocation} from 'react-router-dom';

import {EmptyMessage, UL} from './styled';
import {Task} from './Task';
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

const getQueryVariables = (filter: string, {username}: {username?: string}) => {
  switch (filter) {
    case FilterValues.ClaimedByMe: {
      return {
        assignee: username,
      };
    }
    case FilterValues.Unclaimed: {
      return {
        assigned: false,
      };
    }
    case FilterValues.Completed: {
      return {
        state: 'COMPLETED',
      };
    }
    case FilterValues.AllOpen:
    default: {
      return {};
    }
  }
};

const Tasks: React.FC = () => {
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;
  const isClaimedByMeFilter = filter === FilterValues.ClaimedByMe;
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER, {
    skip: !isClaimedByMeFilter,
  });
  const {data, loading} = useQuery<GetTasks, GetTasksVariables>(GET_TASKS, {
    skip: userData === undefined && isClaimedByMeFilter,
    variables: getQueryVariables(filter, {
      username: userData?.currentUser.username,
    }),
  });

  if (loading || data === undefined) {
    return null;
  }

  const {tasks} = data;

  return (
    <UL>
      {tasks.length > 0 ? (
        tasks.map((task) => {
          return (
            <Task
              key={task.id}
              taskId={task.id}
              name={task.name}
              workflowName={task.workflowName}
              assignee={task.assignee}
              creationTime={task.creationTime}
            />
          );
        })
      ) : (
        <EmptyMessage>There are no tasks available.</EmptyMessage>
      )}
    </UL>
  );
};

export {Tasks};
