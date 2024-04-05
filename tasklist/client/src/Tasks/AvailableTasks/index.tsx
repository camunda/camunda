/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef} from 'react';
import {
  EmptyMessage,
  ListContainer,
  Container,
  EmptyMessageText,
  EmptyListIcon,
} from './styled';
import {Task} from './Task';
import {Stack} from '@carbon/react';
import {Skeleton} from './Skeleton';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {Task as TaskType} from 'modules/types';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {BodyCompact, BodyLong} from 'modules/components/FontTokens';

type Props = {
  onScrollUp: () => Promise<TaskType[]>;
  onScrollDown: () => Promise<TaskType[]>;
  tasks: TaskType[];
  loading: boolean;
};

const AvailableTasks: React.FC<Props> = ({
  loading,
  onScrollDown,
  onScrollUp,
  tasks,
}) => {
  const taskRef = useRef<HTMLDivElement | null>(null);
  const scrollableListRef = useRef<HTMLDivElement | null>(null);
  const {filter} = useTaskFilters();
  const {data, isInitialLoading} = useCurrentUser();
  const isLoading = isInitialLoading || loading;

  useEffect(() => {
    scrollableListRef?.current?.scrollTo?.(0, 0);
  }, [filter]);

  return (
    <Container
      $enablePadding={tasks.length === 0 && !isLoading}
      title="Available tasks"
    >
      {isLoading ? (
        <Skeleton />
      ) : (
        <>
          {tasks.length > 0 && (
            <ListContainer
              data-testid="scrollable-list"
              ref={scrollableListRef}
              onScroll={async (event) => {
                const target = event.target as HTMLDivElement;

                if (
                  target.scrollHeight -
                    target.clientHeight -
                    target.scrollTop <=
                  0
                ) {
                  await onScrollDown();
                } else if (target.scrollTop === 0) {
                  const previousTasks = await onScrollUp();

                  target.scrollTop =
                    (taskRef?.current?.clientHeight ?? 0) *
                    previousTasks.length;
                }
              }}
              tabIndex={-1}
            >
              {tasks.map((task, i) => {
                return (
                  <Task
                    ref={taskRef}
                    key={task.id}
                    taskId={task.id}
                    name={task.name}
                    processName={task.processName}
                    context={task.context}
                    assignee={task.assignee}
                    creationDate={task.creationDate}
                    followUpDate={task.followUpDate}
                    dueDate={task.dueDate}
                    completionDate={task.completionDate}
                    currentUser={data!}
                    position={i}
                  />
                );
              })}
            </ListContainer>
          )}
          {tasks.length === 0 && (
            <Stack as={EmptyMessage} gap={5} orientation="horizontal">
              <EmptyListIcon size={24} alt="" />
              <Stack gap={1} as={EmptyMessageText}>
                <BodyCompact $variant="02">No tasks found</BodyCompact>
                <BodyLong $color="secondary">
                  There are no tasks matching your filter criteria.
                </BodyLong>
              </Stack>
            </Stack>
          )}
        </>
      )}
    </Container>
  );
};

export {AvailableTasks};
