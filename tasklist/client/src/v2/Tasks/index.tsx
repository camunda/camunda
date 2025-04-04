/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useState} from 'react';
import {Outlet, useLocation} from 'react-router-dom';
import {Stack} from '@carbon/react';
import {observer} from 'mobx-react-lite';
import {useTasks} from 'v2/api/useTasks.query';
import {useTaskFilters} from 'v2/features/tasks/filters/useTaskFilters';
import {useAutoSelectNextTask} from 'common/tasks/next-task/useAutoSelectNextTask';
import {useTranslation} from 'react-i18next';
import {autoSelectNextTaskStore} from 'common/tasks/next-task/autoSelectFirstTask';
import {pages} from 'common/routing';
import {Options} from 'common/tasks/available-tasks/Options';
import {Filters} from 'common/tasks/available-tasks/Filters';
import {AvailableTasks} from './AvailableTasks';
import styles from 'common/tasks/page.module.scss';
import {CollapsiblePanel} from 'common/tasks/available-tasks/CollapsiblePanel';

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
        location.pathname !== pages.taskDetails(tasks[0].userTaskKey.toString())
      ) {
        goToTask(tasks[0].userTaskKey.toString());
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
        goToTask(tasks[0].userTaskKey.toString());
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
  const {t} = useTranslation();
  const {
    fetchPreviousPage,
    fetchNextPage,
    isLoading,
    isPending,
    data,
    hasNextPage,
    hasPreviousPage,
  } = useTasks(filters);
  const tasks = useMemo(() => data?.pages.flat() ?? [], [data]);

  useAutoSelectNextTaskSideEffects();

  const {goToTask: autoSelectGoToTask} = useAutoSelectNextTask();

  const onAutoSelectToggle = useCallback(
    (state: boolean) => {
      if (state && location.pathname === pages.initial) {
        const openTasks = tasks.filter(({state}) => state === 'CREATED');
        if (openTasks.length > 0) {
          autoSelectGoToTask(openTasks[0].userTaskKey.toString());
        }
      }
    },
    [autoSelectGoToTask, tasks],
  );

  const handleScrollDown = useCallback(async () => {
    const result = await fetchNextPage();
    return result.data?.pages[result.data.pages.length - 1] ?? [];
  }, [fetchNextPage]);

  const handleScrollUp = useCallback(async () => {
    const result = await fetchPreviousPage();
    return result.data?.pages[0] ?? [];
  }, [fetchPreviousPage]);

  return (
    <main className={styles.container}>
      <CollapsiblePanel />
      <Stack
        as="section"
        className={styles.tasksPanel}
        aria-label={t('tasksPanelLabel')}
      >
        <Filters disabled={isPending} />
        <AvailableTasks
          loading={isLoading}
          onScrollDown={handleScrollDown}
          onScrollUp={handleScrollUp}
          hasNextPage={hasNextPage}
          hasPreviousPage={hasPreviousPage}
          tasks={tasks}
        />
        <Options onAutoSelectToggle={onAutoSelectToggle} />
      </Stack>
      <div
        className={styles.detailsPanel}
        aria-label={t('availableTasksTitle')}
      >
        <Outlet />
      </div>
    </main>
  );
});

Tasks.displayName = 'Tasks';

export {Tasks as Component};
