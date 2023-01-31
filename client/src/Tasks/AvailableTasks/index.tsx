/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef} from 'react';
import {
  EmptyMessage,
  UL,
  Container,
  EmptyMessageFirstLine,
  EmptyMessageSecondLine,
  EmptyMessageText,
  EmptyListIcon,
} from './styled';
import {Task} from './Task';
import {useTasks} from 'modules/hooks/useTasks';
import {useLocation} from 'react-router-dom';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {FilterValues} from 'modules/constants/filterValues';
import {Stack} from '@carbon/react';
import {Skeleton} from './Skeleton';

const AvailableTasks: React.FC = () => {
  const {
    fetchPreviousTasks,
    fetchNextTasks,
    shouldFetchMoreTasks,
    loading,
    tasks,
  } = useTasks({withPolling: true});
  const taskRef = useRef<HTMLDivElement>(null);
  const scrollableListRef = useRef<HTMLUListElement>(null);
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;

  useEffect(() => {
    scrollableListRef?.current?.scrollTo?.(0, 0);
  }, [filter]);

  return (
    <Container $enablePadding={tasks.length === 0 && !loading}>
      {loading && <Skeleton />}
      {tasks.length > 0 && (
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
          tabIndex={-1}
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
        </UL>
      )}
      {tasks.length === 0 && !loading && (
        <Stack as={EmptyMessage} gap={5} orientation="horizontal">
          <EmptyListIcon size={24} alt="" />
          <Stack gap={1} as={EmptyMessageText}>
            <EmptyMessageFirstLine>No tasks found</EmptyMessageFirstLine>
            <EmptyMessageSecondLine>
              There are no tasks matching your filter criteria.
            </EmptyMessageSecondLine>
          </Stack>
        </Stack>
      )}
    </Container>
  );
};

export {AvailableTasks};
