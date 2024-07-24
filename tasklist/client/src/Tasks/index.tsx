/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {Outlet, useLocation} from 'react-router-dom';
import {Stack} from '@carbon/react';
import {observer} from 'mobx-react-lite';
import {useTasks} from 'modules/queries/useTasks';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {useAutoSelectNextTask} from 'modules/auto-select-task/useAutoSelectNextTask';
import {useTranslation} from 'react-i18next';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';
import {pages} from 'modules/routing';
import {Options} from './Options';
import {Filters} from './Filters';
import {AvailableTasks} from './AvailableTasks';
import styles from './styles.module.scss';
import {CollapsiblePanel} from './CollapsiblePanel';

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
  const {t} = useTranslation();
  const {fetchPreviousTasks, fetchNextTasks, isLoading, isPending, data} =
    useTasks(filters);
  const tasks = data?.pages.flat() ?? [];

  useAutoSelectNextTaskSideEffects();

  const {goToTask: autoSelectGoToTask} = useAutoSelectNextTask();

  const onAutoSelectToggle = (state: boolean) => {
    if (state && tasks.length > 0 && location.pathname === pages.initial) {
      autoSelectGoToTask(tasks[0].id);
    }
  };

  return (
    <main className={styles.container}>
      <CollapsiblePanel />
      <Stack as="section" className={styles.tasksPanel} aria-label={t('appPanel')}> {/* Title for screen readers */}
        <Filters disabled={isPending} />
        <AvailableTasks
          loading={isLoading}
          onScrollDown={fetchNextTasks}
          onScrollUp={fetchPreviousTasks}
          tasks={tasks}
        />
        <Options onAutoSelectToggle={onAutoSelectToggle} />
      </Stack>
      <section className={styles.detailsPanel} aria-label={t('availableTasksTitle')}> {/* Title for screen readers */}
        <Outlet />
      </section>
    </main>
  );
});

Tasks.displayName = 'Tasks';

export {Tasks as Component};
