/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, TasksPanel, DetailsPanel} from './styled';
import {Filters} from './Filters';
import {AvailableTasks} from './AvailableTasks';
import {Outlet, useLocation} from 'react-router-dom';
import {useTasks} from 'modules/queries/useTasks';
import {Options} from './Options';
import {useEffect, useMemo, useState} from 'react';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {useAutoSelectNextTask} from 'modules/auto-select-task/useAutoSelectNextTask';
import {observer} from 'mobx-react-lite';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';
import {pages} from 'modules/routing';

function useAutoSelectNextTaskSideEffects() {
  const {enabled} = autoSelectNextTaskStore;
  const filters = useTaskFilters();
  const {isLoading, isFetching, data} = useTasks(filters);
  const location = useLocation();
  const tasks = useMemo(() => data?.pages[0] ?? [], [data?.pages]);

  const {goToTask} = useAutoSelectNextTask();

  // We cannot call `navigate` during a render, we need to defer this with useEffect

  // If the filters change, pick the first task when we finish fetching...
  const filtersKey = JSON.stringify(filters);
  const [previousFilters, setPreviousFilters] = useState<string>(filtersKey);
  useEffect(() => {
    if (previousFilters !== filtersKey && !isFetching) {
      setPreviousFilters(filtersKey);
      if (
        enabled &&
        tasks.length > 0 &&
        location.pathname !== pages.taskDetails(tasks[0].id)
      ) {
        goToTask(tasks[0].id);
      }
    }
  }, [
    enabled,
    filters,
    filtersKey,
    goToTask,
    isFetching,
    location.pathname,
    previousFilters,
    tasks,
  ]);

  // If we are on the initial page, when we finish loading tasks...
  const [isFinishedLoading, setIsFinishedLoading] = useState(false);
  useEffect(() => {
    if (!isFinishedLoading && !isLoading) {
      setIsFinishedLoading(true);
      if (enabled && tasks.length > 0 && location.pathname === pages.initial) {
        goToTask(tasks[0].id);
      }
    }
  }, [
    location.pathname,
    tasks,
    isLoading,
    isFinishedLoading,
    enabled,
    goToTask,
  ]);
}

const Tasks: React.FC = observer(() => {
  const filters = useTaskFilters();
  const {
    fetchPreviousTasks,
    fetchNextTasks,
    isInitialLoading,
    isLoading,
    data,
  } = useTasks(filters);
  const tasks = data?.pages.flat() ?? [];

  useAutoSelectNextTaskSideEffects();

  const {goToTask: autoSelectGoToTask} = useAutoSelectNextTask();

  const onAutoSelectToggle = (state: boolean) => {
    if (state && tasks.length > 0 && location.pathname === pages.initial) {
      autoSelectGoToTask(tasks[0].id);
    }
  };

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
        <Options onAutoSelectToggle={onAutoSelectToggle} />
      </TasksPanel>
      <DetailsPanel>
        <Outlet />
      </DetailsPanel>
    </Container>
  );
});

Tasks.displayName = 'Tasks';

export {Tasks as Component};
