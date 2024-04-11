/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useEffect, useMemo, useState} from 'react';
import {Outlet, useLocation} from 'react-router-dom';
import {Stack} from '@carbon/react';
import {observer} from 'mobx-react-lite';
import {useTasks} from 'modules/queries/useTasks';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {useAutoSelectNextTask} from 'modules/auto-select-task/useAutoSelectNextTask';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';
import {pages} from 'modules/routing';
import {Options} from './Options';
import {Filters} from './Filters';
import {AvailableTasks} from './AvailableTasks';
import styles from './styles.module.scss';

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
    <main className={styles.container}>
      <Stack as="section" className={styles.tasksPanel} aria-label="Left panel">
        <Filters disabled={isLoading} />
        <AvailableTasks
          loading={isInitialLoading}
          onScrollDown={fetchNextTasks}
          onScrollUp={fetchPreviousTasks}
          tasks={tasks}
        />
        <Options onAutoSelectToggle={onAutoSelectToggle} />
      </Stack>
      <section className={styles.detailsPanel}>
        <Outlet />
      </section>
    </main>
  );
});

Tasks.displayName = 'Tasks';

export {Tasks as Component};
