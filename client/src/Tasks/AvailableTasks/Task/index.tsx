/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {
  Row,
  Name,
  Process,
  Assignee,
  CreationTime,
  TaskLink,
  Stack,
  Container,
  Tag,
} from './styled';
import {Pages} from 'modules/constants/pages';
import {formatDate} from 'modules/utils/formatDate';
import {Task as TaskType} from 'modules/types';
import {useLocation, useMatch} from 'react-router-dom';
import {useQuery} from '@apollo/client';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';

type Props = {
  taskId: TaskType['id'];
  name: TaskType['name'];
  processName: TaskType['processName'];
  assignee: TaskType['assignee'];
  creationTime: TaskType['creationTime'];
};

const Task = React.forwardRef<HTMLElement, Props>(
  ({taskId, name, processName, assignee, creationTime}, ref) => {
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

    return (
      <Container
        className={match?.params?.id === taskId ? 'active' : undefined}
      >
        <TaskLink
          to={{
            ...location,
            pathname: Pages.TaskDetails(taskId),
          }}
          aria-label={
            isUnassigned
              ? `Unassgined task: ${name}`
              : `${
                  isAssignedToMe ? `Task assigned to me` : 'Assigned task'
                }: ${name}`
          }
        >
          <Stack data-testid={`task-${taskId}`} gap={3} ref={ref}>
            <Row>
              <Name>{name}</Name>
              <Process>{processName}</Process>
            </Row>
            <Row>
              <Assignee>
                {isUnassigned ? (
                  'Unassigned'
                ) : (
                  <Tag
                    type="gray"
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
              </Assignee>
            </Row>
            <Row data-testid="creation-time">
              {formatDate(creationTime) === '' ? null : (
                <CreationTime title={`Created at ${formatDate(creationTime)}`}>
                  Created
                  <br />
                  {formatDate(creationTime)}
                </CreationTime>
              )}
            </Row>
          </Stack>
        </TaskLink>
      </Container>
    );
  },
);

export {Task};
