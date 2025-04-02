/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {Stack} from '@carbon/react';
import {Search} from '@carbon/react/icons';
import {useTaskFilters} from 'v1/features/tasks/filters/useTaskFilters';
import type {Task as TaskType} from 'v1/api/types';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {AvailableTaskItem} from 'common/tasks/available-tasks/AvailableTaskItem';
import {AvailableTasksSkeleton} from 'common/tasks/available-tasks/AvailableTasksSkeleton';
import styles from 'common/tasks/available-tasks/styles.module.scss';
import cn from 'classnames';

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
  const {data, isLoading: isLoadingUser} = useCurrentUser();
  const isLoading = isLoadingUser || loading;

  useEffect(() => {
    scrollableListRef?.current?.scrollTo?.(0, 0);
  }, [filter]);

  const {t} = useTranslation();

  return (
    <div
      className={cn(styles.container, {
        [styles.containerPadding]: tasks.length === 0 && !isLoading,
      })}
      title={t('availableTasksTitle')}
    >
      {isLoading ? (
        <AvailableTasksSkeleton className={styles.listContainer} />
      ) : (
        <>
          {tasks.length > 0 && (
            <div
              className={styles.listContainer}
              data-testid="scrollable-list"
              ref={scrollableListRef}
              onScroll={async (event) => {
                const target = event.target as HTMLDivElement;

                if (
                  Math.floor(
                    target.scrollHeight -
                      target.clientHeight -
                      target.scrollTop,
                  ) <= 0
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
                  <AvailableTaskItem
                    ref={taskRef}
                    key={task.id}
                    taskId={task.id}
                    displayName={task.name}
                    processDisplayName={task.processName}
                    context={task.context}
                    assignee={task.assignee}
                    creationDate={task.creationDate}
                    followUpDate={task.followUpDate}
                    dueDate={task.dueDate}
                    completionDate={task.completionDate}
                    priority={task.priority}
                    currentUser={data!}
                    position={i}
                  />
                );
              })}
            </div>
          )}
          {tasks.length === 0 && (
            <Stack
              gap={5}
              orientation="horizontal"
              className={styles.emptyMessage}
            >
              <Search size={24} aria-hidden className={styles.emptyListIcon} />
              <Stack gap={1} className={styles.emptyMessageText}>
                <span className={styles.emptyMessageHeading}>
                  {t('availableTasksNoTasksFoundInfo')}
                </span>
                <span className={styles.emptyMessageBody}>
                  {t('availableTasksNoTasksMatchingCriteriaInfo')}
                </span>
              </Stack>
            </Stack>
          )}
        </>
      )}
    </div>
  );
};

export {AvailableTasks};
