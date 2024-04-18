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

import React, {useMemo} from 'react';
import {NavLink, useLocation, useMatch} from 'react-router-dom';
import {isBefore} from 'date-fns';
import {Stack} from '@carbon/react';
import {
  Calendar,
  CheckmarkFilled,
  Warning,
  Notification,
} from '@carbon/icons-react';
import {pages} from 'modules/routing';
import {
  formatISODate,
  formatISODateTime,
} from 'modules/utils/formatDateRelative';
import {unraw} from 'modules/utils/unraw';
import {CurrentUser, Task as TaskType} from 'modules/types';
import {TaskFilters, useTaskFilters} from 'modules/hooks/useTaskFilters';
import {encodeTaskOpenedRef} from 'modules/utils/reftags';
import {AssigneeTag} from 'Tasks/AssigneeTag';
import {DateLabel} from './DateLabel';
import styles from './styles.module.scss';
import cn from 'classnames';

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
      return `Task assigned to me: ${name}`;
    } else {
      return `Assigned task: ${name}`;
    }
  } else {
    return `Unassigned task: ${name}`;
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
      currentUser,
      position,
    },
    ref,
  ) => {
    const match = useMatch('/:id');
    const location = useLocation();
    const isActive = match?.params?.id === taskId;
    const {filter, sortBy} = useTaskFilters();
    const creationDate = useMemo(
      () => formatISODateTime(creationDateString),
      [creationDateString],
    );

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
            ref={ref}
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

            <div className={cn(styles.flex, styles.flexColumn)}>
              <span>
                <AssigneeTag currentUser={currentUser} assignee={assignee} />
              </span>
            </div>
            <div
              data-testid="dates"
              className={cn(styles.flex, styles.flexRow, styles.alignItemsEnd)}
            >
              {creationDate ? (
                <DateLabel
                  date={creationDate}
                  relativeLabel="Created"
                  absoluteLabel="Created on"
                  icon={<Calendar className={styles.inlineIcon} />}
                />
              ) : null}
              {secondaryDate.followUpDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.followUpDate}
                  relativeLabel="Follow-up"
                  absoluteLabel="Follow-up on"
                  icon={
                    <Notification className={styles.inlineIcon} color="blue" />
                  }
                  align="top-right"
                />
              ) : null}
              {secondaryDate.overDueDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.overDueDate}
                  relativeLabel="Overdue"
                  absoluteLabel="Overdue"
                  icon={<Warning className={styles.inlineIcon} color="red" />}
                  align="top-right"
                />
              ) : null}
              {secondaryDate.dueDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.dueDate}
                  relativeLabel="Due"
                  absoluteLabel="Due on"
                  align="top-right"
                />
              ) : null}
              {secondaryDate.completionDate !== undefined ? (
                <DateLabel
                  date={secondaryDate.completionDate}
                  relativeLabel="Completed"
                  absoluteLabel="Completed on"
                  icon={
                    <CheckmarkFilled
                      className={styles.inlineIcon}
                      color="green"
                    />
                  }
                  align="top-right"
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
