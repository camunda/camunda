/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Row, Label, TaskLink, Stack, Container, Tag} from './styled';
import {pages} from 'modules/routing';
import {formatDate} from 'modules/utils/formatDate';
import {Task as TaskType} from 'modules/types';
import {useLocation, useMatch} from 'react-router-dom';
import {useQuery} from '@apollo/client';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {BodyCompact} from 'modules/components/FontTokens';

type Props = {
  taskId: TaskType['id'];
  name: TaskType['name'];
  processName: TaskType['processName'];
  assignee: TaskType['assignee'];
  creationTime: TaskType['creationTime'];
  followUpDate: TaskType['followUpDate'];
  dueDate: TaskType['dueDate'];
};

const Task = React.forwardRef<HTMLElement, Props>(
  (
    {taskId, name, processName, assignee, creationTime, followUpDate, dueDate},
    ref,
  ) => {
    const {data} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
    const {
      currentUser: {userId, displayName},
    } = data ?? {
      currentUser: {
        userId: null,
        displayName: null,
      },
    };
    const isUnassigned = assignee === null || userId === null;
    const isAssignedToMe = assignee === userId;
    const match = useMatch('/:id');
    const location = useLocation();
    const isActive = match?.params?.id === taskId;
    const {sortBy} = useTaskFilters();
    const showFollowupDate =
      followUpDate !== null &&
      formatDate(followUpDate) !== '' &&
      sortBy === 'follow-up';
    const showDueDate =
      dueDate !== null && formatDate(dueDate) !== '' && sortBy !== 'follow-up';
    return (
      <Container className={isActive ? 'active' : undefined}>
        <TaskLink
          to={{
            ...location,
            pathname: pages.taskDetails(taskId),
          }}
          aria-label={
            isUnassigned
              ? `Unassigned task: ${name}`
              : `${
                  isAssignedToMe ? `Task assigned to me` : 'Assigned task'
                }: ${name}`
          }
        >
          <Stack data-testid={`task-${taskId}`} gap={3} ref={ref}>
            <Row>
              <BodyCompact $variant="02">{name}</BodyCompact>
              <Label $variant="secondary">{processName}</Label>
            </Row>
            <Row>
              <Label $variant="secondary">
                {isUnassigned ? (
                  'Unassigned'
                ) : (
                  <Tag
                    type={isActive ? 'high-contrast' : 'gray'}
                    size="sm"
                    title={
                      isAssignedToMe
                        ? undefined
                        : `Task assigned to ${displayName}`
                    }
                    unselectable="off"
                  >
                    {isAssignedToMe ? 'Assigned to me' : 'Assigned'}
                  </Tag>
                )}
              </Label>
            </Row>
            <Row data-testid="creation-time" $direction="row">
              {formatDate(creationTime) === '' ? null : (
                <Label
                  $variant="primary"
                  title={`Created at ${formatDate(creationTime)}`}
                >
                  <Label $variant="secondary">Created</Label>
                  <br />
                  {formatDate(creationTime)}
                </Label>
              )}
              {showFollowupDate ? (
                <Label
                  $variant="primary"
                  title={`Follow-up at ${formatDate(followUpDate!, false)}`}
                >
                  <Label $variant="secondary">Follow-up</Label>
                  <br />
                  {formatDate(followUpDate!, false)}
                </Label>
              ) : null}
              {showDueDate ? (
                <Label
                  $variant="primary"
                  title={`Due at ${formatDate(dueDate!, false)}`}
                >
                  <Label $variant="secondary">Due</Label>
                  <br />
                  {formatDate(dueDate!, false)}
                </Label>
              ) : null}
            </Row>
          </Stack>
        </TaskLink>
      </Container>
    );
  },
);

export {Task};
