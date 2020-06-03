/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  Entry,
  TaskInfo,
  TaskName,
  WorkflowName,
  TaskStatus,
  Assignee,
  CreationTime,
} from './styled';
import {useParams} from 'react-router-dom';
import {Link} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';
import {formatDate} from 'modules/utils/formatDate';
import {Task as TaskType} from 'modules/types';

interface Props {
  taskKey: TaskType['key'];
  name: TaskType['name'];
  workflowName: TaskType['workflowName'];
  assignee: TaskType['assignee'];
  creationTime: TaskType['creationTime'];
}

const Task: React.FC<Props> = ({
  taskKey,
  name,
  workflowName,
  assignee,
  creationTime,
}) => {
  const {key} = useParams();

  return (
    <Link to={Pages.TaskDetails(taskKey)}>
      <Entry isSelected={key === taskKey} data-testid={`task-${taskKey}`}>
        <TaskInfo>
          <TaskName>{name}</TaskName>
          <WorkflowName>{workflowName}</WorkflowName>
        </TaskInfo>
        <TaskStatus>
          <Assignee data-testid="assignee">
            {assignee ? `${assignee.firstname} ${assignee.lastname}` : '--'}
          </Assignee>

          <CreationTime data-testid="creation-time">
            {formatDate(creationTime)}
          </CreationTime>
        </TaskStatus>
      </Entry>
    </Link>
  );
};

export {Task};
