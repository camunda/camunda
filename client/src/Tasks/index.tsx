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
import {Pages} from 'modules/constants/pages';
import {useTasks} from 'modules/hooks/useTasks';

const Tasks: React.FC = () => {
  const {fetchPreviousTasks, fetchNextTasks, loading, tasks, refetch} =
    useTasks();

  return (
    <Container>
      <TasksPanel title="Left panel" forwardedAs="section">
        <Filters disabled={loading} />
        <AvailableTasks
          loading={loading}
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
                isLoadingTasks={loading}
              />
            }
          />
          <Route
            path={Pages.TaskDetails()}
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
