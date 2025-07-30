/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useMemo} from 'react';
import {NavLink, useLocation} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
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
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/8.8';
import {useMultiModeTaskFilters} from 'common/tasks/filters/useMultiModeTaskFilters';
import {encodeTaskOpenedRef} from 'common/tracking/reftags';
import {AssigneeTag} from 'common/components/AssigneeTag';
import {DateLabel} from 'common/tasks/available-tasks/DateLabel';
import {PriorityLabel} from 'common/tasks/available-tasks/PriorityLabel';
import styles from './styles.module.scss';
import cn from 'classnames';
import {useIsCurrentTaskOpen} from './useIsCurrentTaskOpen';
import {getNavLinkLabel} from './getNavLinkLabel';
import {getSecondaryDate} from './getSecondaryDate';

type Props = {
  taskId: string;
  displayName: string;
  processDisplayName: string;
  context?: string | null;
  assignee: string | null | undefined;
  creationDate: string;
  followUpDate: string | null | undefined;
  dueDate: string | null | undefined;
  completionDate: string | null | undefined;
  priority: number | null;
  currentUser: CurrentUser;
  position: number;
};

const AvailableTaskItem = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      taskId,
      displayName,
      processDisplayName,
      context = null,
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
    const {filter, sortBy} = useMultiModeTaskFilters();
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
            displayName,
            assigneeId: assignee,
            currentUsername: currentUser.username,
          })}
        >
          <Stack
            className={styles.fullWidthAndHeight}
            data-testid={`task-${taskId}`}
            gap={3}
            ref={ref}
          >
            <div className={cn(styles.flex, styles.flexColumn)}>
              <span className={styles.name}>{displayName}</span>
              <span className={styles.label}>{processDisplayName}</span>
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

export {AvailableTaskItem};
