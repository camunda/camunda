/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useRef} from 'react';

import {EmptyMessage, UL, Container} from './styled';
import {Task} from './Task';
import {LoadingOverlay} from 'modules/components/LoadingOverlay';
import {useTasks} from '../useTasks';
import {useLocation} from 'react-router-dom';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {FilterValues} from 'modules/constants/filterValues';

const Tasks: React.FC = () => {
  const {
    tasks,
    loading,
    isFirstLoad,
    fetchPreviousTasks,
    fetchNextTasks,
    shouldFetchMoreTasks,
  } = useTasks({withPolling: true});

  const taskRef = useRef<HTMLLIElement>(null);
  const scrollableListRef = useRef<HTMLUListElement>(null);

  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;

  useEffect(() => {
    scrollableListRef?.current?.scrollTo?.(0, 0);
  }, [filter]);

  return (
    <Container isLoading={loading}>
      {loading && <LoadingOverlay data-testid="tasks-loading-overlay" />}
      <UL
        data-testid="scrollable-list"
        ref={scrollableListRef}
        onScroll={async (event) => {
          const target = event.target as HTMLDivElement;

          if (!shouldFetchMoreTasks) {
            return;
          }

          if (
            target.scrollHeight - target.clientHeight - target.scrollTop <=
            0
          ) {
            await fetchNextTasks();
          } else if (target.scrollTop === 0) {
            const previousTasks = await fetchPreviousTasks();

            target.scrollTop =
              (taskRef?.current?.clientHeight ?? 0) * previousTasks.length;
          }
        }}
      >
        {tasks.map((task) => {
          return (
            <Task
              ref={taskRef}
              key={task.id}
              taskId={task.id}
              name={task.name}
              processName={task.processName}
              assignee={task.assignee}
              creationTime={task.creationTime}
            />
          );
        })}
        {tasks.length === 0 && !isFirstLoad ? (
          <EmptyMessage>No Tasks available</EmptyMessage>
        ) : null}
      </UL>
    </Container>
  );
};

export {Tasks};
