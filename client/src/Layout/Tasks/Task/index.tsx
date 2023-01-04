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
  Li,
} from './styled';
import {Pages} from 'modules/constants/pages';
import {formatDate} from 'modules/utils/formatDate';
import {Task as TaskType} from 'modules/types';
import {useLocation, useMatch} from 'react-router-dom';
interface Props {
  taskId: TaskType['id'];
  name: TaskType['name'];
  processName: TaskType['processName'];
  assignee: TaskType['assignee'];
  creationTime: TaskType['creationTime'];
}

const Task = React.forwardRef<HTMLDivElement, Props>(
  ({taskId, name, processName, assignee, creationTime}, ref) => {
    const match = useMatch('/:id');
    const location = useLocation();

    return (
      <Li className={match?.params?.id === taskId ? 'active' : undefined}>
        <TaskLink
          to={{
            ...location,
            pathname: Pages.TaskDetails(taskId),
          }}
        >
          <Stack data-testid={`task-${taskId}`} gap={8} ref={ref}>
            <Row>
              <Name>{name}</Name>
              <Process>{processName}</Process>
            </Row>
            <Row>
              <Assignee data-testid="assignee">
                {assignee ? assignee : '--'}
              </Assignee>

              <CreationTime data-testid="creation-time">
                {formatDate(creationTime)}
              </CreationTime>
            </Row>
          </Stack>
        </TaskLink>
      </Li>
    );
  },
);

export {Task};
