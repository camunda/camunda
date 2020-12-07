/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';

import {EmptyMessage, UL, Container} from './styled';
import {Task} from './Task';
import {LoadingOverlay} from 'modules/components/LoadingOverlay';
import {useTasks} from '../useTasks';

const Tasks: React.FC = () => {
  const {tasks, loading, isFirstLoad} = useTasks();

  return (
    <Container isLoading={loading}>
      {loading && <LoadingOverlay data-testid="tasks-loading-overlay" />}
      <UL>
        {tasks.map((task) => {
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
        })}
        {tasks.length === 0 && !isFirstLoad ? (
          <EmptyMessage>There are no Tasks available</EmptyMessage>
        ) : null}
      </UL>
    </Container>
  );
};

export {Tasks};
