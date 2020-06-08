/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {useQuery} from '@apollo/react-hooks';

import {EmptyMessage} from './styled';
import {Task} from './Task';
import {GET_TASKS, GetTasks} from 'modules/queries/get-tasks';

const Tasks: React.FC = () => {
  const {data, loading} = useQuery<GetTasks>(GET_TASKS);

  if (loading || data === undefined) {
    return null;
  }
  const {tasks} = data;
  return (
    <ul>
      {tasks.length > 0 ? (
        tasks.map((task) => {
          return (
            <Task
              key={task.key}
              taskKey={task.key}
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
    </ul>
  );
};

export {Tasks};
