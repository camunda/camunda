/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useMemo} from 'react';
import {Row, Label, TaskLink, Stack, Container, DateLabel} from './styled';
import {pages} from 'modules/routing';
import {formatDate} from 'modules/utils/formatDate';
import {CurrentUser, Task as TaskType} from 'modules/types';
import {useLocation, useMatch} from 'react-router-dom';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {BodyCompact} from 'modules/components/FontTokens';
import {encodeTaskOpenedRef} from 'modules/utils/reftags';
import {AssigneeTag} from 'Tasks/AssigneeTag';
import {
  Calendar,
  CheckmarkFilled,
  Warning,
  Notification,
} from '@carbon/icons-react';
import {compareAsc} from 'date-fns';

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

const Task = React.forwardRef<HTMLElement, Props>(
  (
    {
      taskId,
      name,
      processName,
      context,
      assignee,
      creationDate,
      followUpDate,
      dueDate,
      completionDate,
      currentUser,
      position,
    },
    ref,
  ) => {
    const {userId} = currentUser;
    const isAssigned = assignee !== null;
    const isAssignedToCurrentUser = assignee === userId;
    const match = useMatch('/:id');
    const location = useLocation();
    const isActive = match?.params?.id === taskId;
    const {filter, sortBy} = useTaskFilters();
    const hasCompletionDate =
      completionDate !== null && formatDate(completionDate) !== '';
    const hasDueDate = dueDate !== null && formatDate(dueDate) !== '';
    const hasFollowupDate =
      followUpDate !== null && formatDate(followUpDate) !== '';
    const todaysDate = new Date().toISOString();
    const isOverdue =
      !hasCompletionDate &&
      hasDueDate &&
      compareAsc(dueDate, todaysDate) === -1;
    const isFollowupBeforeDueDate =
      hasDueDate && hasFollowupDate && compareAsc(dueDate, followUpDate) === 1;

    const showCompletionDate =
      hasCompletionDate && sortBy !== 'due' && sortBy !== 'follow-up';
    const showFollowupDate =
      !showCompletionDate &&
      hasFollowupDate &&
      sortBy !== 'due' &&
      sortBy !== 'completion' &&
      (sortBy === 'follow-up' || (!isOverdue && isFollowupBeforeDueDate));
    const showDueDate =
      !showCompletionDate &&
      !showFollowupDate &&
      hasDueDate &&
      sortBy !== 'follow-up';

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

    return (
      <Container $active={isActive}>
        <TaskLink
          to={{
            ...location,
            pathname: pages.taskDetails(taskId),
            search: searchWithRefTag.toString(),
          }}
          aria-label={
            isAssigned
              ? `${
                  isAssignedToCurrentUser
                    ? `Task assigned to me`
                    : 'Assigned task'
                }: ${name}`
              : `Unassigned task: ${name}`
          }
        >
          <Stack data-testid={`task-${taskId}`} gap={3} ref={ref}>
            <Row>
              <BodyCompact $variant="02">{name}</BodyCompact>
              <Label $variant="secondary">{processName}</Label>
            </Row>

            {context === null ? null : (
              <Row>
                <Label
                  $variant="secondary"
                  $shouldWrap
                  title={context.replace(/\\n/g, '\n')}
                >
                  {context.split('\\n').map((line, index) => (
                    <div key={index}>{line}</div>
                  ))}
                </Label>
              </Row>
            )}

            <Row>
              <Label $variant="secondary">
                <AssigneeTag currentUser={currentUser} assignee={assignee} />
              </Label>
            </Row>
            <Row data-testid="creation-time" $direction="row">
              {formatDate(creationDate) === '' ? null : (
                <DateLabel
                  $variant="primary"
                  title={`Created at ${formatDate(creationDate)}`}
                >
                  <Stack orientation="vertical" gap={1}>
                    <Label $variant="secondary">Created</Label>
                    <Stack orientation="horizontal" gap={2}>
                      <Calendar />
                      {formatDate(creationDate)}
                    </Stack>
                  </Stack>
                </DateLabel>
              )}
              {showFollowupDate ? (
                <DateLabel
                  $variant="primary"
                  title={`Follow-up at ${formatDate(followUpDate!, false)}`}
                >
                  <Stack orientation="vertical" gap={1}>
                    <Label $variant="secondary">Follow-up</Label>
                    <Stack orientation="horizontal" gap={2}>
                      <Notification color="blue" />
                      {formatDate(followUpDate!, false)}
                    </Stack>
                  </Stack>
                </DateLabel>
              ) : null}
              {showDueDate ? (
                <DateLabel
                  $variant="primary"
                  title={
                    isOverdue
                      ? `Overdue at ${formatDate(dueDate!, false)}`
                      : `Due at ${formatDate(dueDate!, false)}`
                  }
                >
                  <Stack orientation="vertical" gap={1}>
                    {isOverdue ? (
                      <>
                        <Label $variant="secondary">Overdue</Label>
                        <Stack orientation="horizontal" gap={2}>
                          <Warning color="red" />
                          {formatDate(dueDate!, false)}
                        </Stack>
                      </>
                    ) : (
                      <>
                        <Label $variant="secondary">Due</Label>
                        {formatDate(dueDate!, false)}
                      </>
                    )}
                  </Stack>
                </DateLabel>
              ) : null}
              {showCompletionDate ? (
                <DateLabel
                  $variant="primary"
                  title={`Completed at ${formatDate(completionDate!, false)}`}
                >
                  <Stack orientation="vertical" gap={1}>
                    <Label $variant="secondary">Completed</Label>
                    <Stack orientation="horizontal" gap={2}>
                      <CheckmarkFilled color="green" />
                      {formatDate(completionDate!, false)}
                    </Stack>
                  </Stack>
                </DateLabel>
              ) : null}
            </Row>
          </Stack>
        </TaskLink>
      </Container>
    );
  },
);

export {Task};
