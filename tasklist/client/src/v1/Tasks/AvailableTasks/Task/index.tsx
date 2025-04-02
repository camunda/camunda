/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo} from 'react';
import {NavLink, useLocation} from 'react-router-dom';
import {t} from 'i18next';
import {useTranslation} from 'react-i18next';
import {isBefore} from 'date-fns';
import {Stack} from '@carbon/react';
import {
  Calendar,
  CheckmarkFilled,
  Warning,
  Notification,
} from '@carbon/icons-react';
import {pages} from 'common/routing';
import {
  formatISODate,
  formatISODateTime,
} from 'common/dates/formatDateRelative';
import {unraw} from './unraw';
import type {Task as TaskType} from 'v1/api/types';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/identity';
import {
  type TaskFilters,
  useTaskFilters,
} from 'v1/features/tasks/filters/useTaskFilters';
import {encodeTaskOpenedRef} from 'common/tracking/reftags';
import {AssigneeTag} from 'v1/Tasks/AssigneeTag';
import {DateLabel} from './DateLabel';
import {PriorityLabel} from './PriorityLabel';
import styles from './styles.module.scss';
import cn from 'classnames';
import {useIsCurrentTaskOpen} from './useIsCurrentTaskOpen';

type Props = {
  taskId: TaskType['id'];
  name: TaskType['name'];
  processName: TaskType['processName'];
  context: TaskType['context'];
  assignee: TaskType['assignee'];
  creationDate: TaskType['creationDate'];
  followUpDate: TaskType['followUpDate'];
  dueDate: TaskType['dueDate'];
  completionDate: TaskType['completionDate'];
  priority: TaskType['priority'];
  currentUser: CurrentUser;
  position: number;
};

function getNavLinkLabel({
  name,
  assigneeId,
  currentUserId,
}: {
  name: string;
  assigneeId: string | null;
  currentUserId: string;
}) {
  const isAssigned = assigneeId !== null;
  const isAssignedToCurrentUser = assigneeId === currentUserId;
  if (isAssigned) {
    if (isAssignedToCurrentUser) {
      return t('availableTasksNavLinkAssignedToMe', {name});
    } else {
      return t('availableTasksNavLinkAssignedTask', {name});
    }
  } else {
    return t('availableTasksNavLinkUnassignedTask', {name});
  }
}

function getSecondaryDate({
  completionDate,
  dueDate,
  followUpDate,
  sortBy,
}: {
  completionDate: ReturnType<typeof formatISODate>;
  dueDate: ReturnType<typeof formatISODate>;
  followUpDate: ReturnType<typeof formatISODate>;
  sortBy: TaskFilters['sortBy'];
}) {
  const now = Date.now();
  const isOverdue = !completionDate && dueDate && isBefore(dueDate.date, now);
  const isFollowupBeforeDueDate =
    dueDate && followUpDate && isBefore(followUpDate.date, dueDate.date);

  if (sortBy === 'creation' && completionDate) {
    return {completionDate};
  } else if (
    sortBy === 'creation' &&
    followUpDate &&
    !isOverdue &&
    isFollowupBeforeDueDate
  ) {
    return {followUpDate};
  } else if (sortBy === 'completion' && completionDate) {
    return {completionDate};
  } else if (sortBy === 'follow-up' && followUpDate) {
    return {followUpDate};
  } else if (dueDate) {
    return isOverdue ? {overDueDate: dueDate} : {dueDate};
  } else {
    return {};
  }
}

const Task = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      taskId,
      name,
      processName,
      context,
      assignee,
      creationDate: creationDateString,
      followUpDate: followUpDateString,
      dueDate: dueDateString,
      completionDate: completionDateString,
      priority,
      currentUser,
      position,
    },
    ref,
  ) => {
    const location = useLocation();
    const isActive = useIsCurrentTaskOpen(taskId);
    const {filter, sortBy} = useTaskFilters();
    const {t} = useTranslation();

    const creationDate = formatISODateTime(creationDateString);
    const completionDate = formatISODate(completionDateString);
    const dueDate = formatISODate(dueDateString);
    const followUpDate = formatISODate(followUpDateString);
    const secondaryDate = getSecondaryDate({
      completionDate,
      dueDate,
      followUpDate,
      sortBy,
    });

    const searchWithRefTag = useMemo(() => {
      const params = new URLSearchParams(location.search);
      params.set(
        'ref',
        encodeTaskOpenedRef({
          by: 'user',
          position,
          filter,
          sorting: sortBy,
        }),
      );
      return params;
    }, [location, position, filter, sortBy]);

    const decodedContext = useMemo(
      () => (context !== null ? unraw(context) : null),
      [context],
    );

    return (
      <article className={cn(styles.container, {[styles.active]: isActive})}>
        <NavLink
          className={styles.taskLink}
          to={{
            ...location,
            pathname: pages.taskDetails(taskId),
            search: searchWithRefTag.toString(),
          }}
          aria-label={getNavLinkLabel({
            name,
            assigneeId: assignee,
            currentUserId: currentUser.userId,
          })}
        >
          <Stack
            className={styles.fullWidthAndHeight}
            data-testid={`task-${taskId}`}
            gap={3}
            ref={ref as React.LegacyRef<React.ReactNode>}
          >
            <div className={cn(styles.flex, styles.flexColumn)}>
              <span className={styles.name}>{name}</span>
              <span className={styles.label}>{processName}</span>
            </div>
            {decodedContext !== null && (
              <div className={cn(styles.flex, styles.flexColumn)}>
                <div
                  className={cn(styles.label, styles.contextWrap)}
                  title={decodedContext}
                >
                  {decodedContext.split('\n').map((line, index) => (
                    <div key={index}>{line}</div>
                  ))}
                </div>
              </div>
            )}

            <div className={cn(styles.flex, styles.flexRow)}>
              <AssigneeTag currentUser={currentUser} assignee={assignee} />
              {priority === null ? null : <PriorityLabel priority={priority} />}
            </div>
            <div
              data-testid="dates"
              className={cn(styles.flex, styles.flexRow, styles.alignItemsEnd)}
            >
              {creationDate ? (
                <DateLabel
                  date={creationDate}
                  relativeLabel={t('availableTasksCreatedRelativeLabel')}
                  absoluteLabel={t('availableTasksCreatedAbsoluteLabel')}
                  icon={<Calendar className={styles.inlineIcon} />}
                />
              ) : null}
              {secondaryDate.followUpDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.followUpDate}
                  relativeLabel={t('availableTasksFollowUpRelativeLabel')}
                  absoluteLabel={t('availableTasksFollowUpAbsoluteLabel')}
                  icon={
                    <Notification className={styles.inlineIcon} color="blue" />
                  }
                  align="top-end"
                />
              ) : null}
              {secondaryDate.overDueDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.overDueDate}
                  relativeLabel={t('availableTasksOverdueRelativeLabel')}
                  absoluteLabel={t('availableTasksOverdueAbsoluteLabel')}
                  icon={<Warning className={styles.inlineIcon} color="red" />}
                  align="top-end"
                />
              ) : null}
              {secondaryDate.dueDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.dueDate}
                  relativeLabel={t('availableTasksDueRelativeLabel')}
                  absoluteLabel={t('availableTasksDueAbsoluteLabel')}
                  align="top-end"
                />
              ) : null}
              {secondaryDate.completionDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.completionDate}
                  relativeLabel={t('availableTasksCompletedRelativeLabel')}
                  absoluteLabel={t('availableTasksCompletedAbsoluteLabel')}
                  icon={
                    <CheckmarkFilled
                      className={styles.inlineIcon}
                      color="green"
                    />
                  }
                  align="top-end"
                />
              ) : null}
            </div>
          </Stack>
        </NavLink>
      </article>
    );
  },
);

export {Task};
