/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, TasksPanel, DetailsPanel} from './styled';
import {Task} from './Task';
import {Filters} from './Filters';
import {AvailableTasks} from './AvailableTasks';
import {EmptyPage} from './EmptyPage';
import {Route, Routes} from 'react-router-dom';
import {pages} from 'modules/routing';
import {useTasks} from 'modules/queries/useTasks';

const Tasks: React.FC = () => {
  const {
    fetchPreviousTasks,
    fetchNextTasks,
    isInitialLoading,
    isLoading,
    data,
    refetch,
  } = useTasks();
  const tasks = data?.pages.flat() ?? [];

  return (
    <Container>
      <TasksPanel aria-label="Left panel" forwardedAs="section">
        <Filters disabled={isLoading} />
        <AvailableTasks
          loading={isInitialLoading}
          onScrollDown={fetchNextTasks}
          onScrollUp={fetchPreviousTasks}
          tasks={tasks}
        />
      </TasksPanel>
      <DetailsPanel>
        <Routes>
          <Route
            index
            element={
              <EmptyPage
                hasNoTasks={tasks.length === 0}
                isLoadingTasks={isLoading}
              />
            }
          />
          <Route
            path={pages.taskDetails()}
            element={
              <Task
                hasRemainingTasks={tasks.length > 0}
                onCompleted={refetch}
              />
            }
          />
        </Routes>
      </DetailsPanel>
    </Container>
  );
};

export {Tasks};
