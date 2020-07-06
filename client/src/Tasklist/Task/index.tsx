/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useParams} from 'react-router-dom';
import {useQuery, useMutation} from '@apollo/react-hooks';
import {Form} from 'react-final-form';

import {TaskStates} from 'modules/constants/taskStates';
import {GET_TASK, GetTask, TaskQueryVariables} from 'modules/queries/get-task';
import {GET_TASK_DETAILS} from 'modules/queries/get-task-details';
import {GET_TASK_VARIABLES} from 'modules/queries/get-task-variables';
import {
  COMPLETE_TASK,
  CompleteTaskVariables,
} from 'modules/mutations/complete-task';

import {PrimaryButton} from 'modules/components/Button/styled';

import {Variables} from './Variables';
import {Details} from './Details';
import {Footer, Form as StyledForm} from './styled';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';

const Task: React.FC = () => {
  const {id} = useParams();

  const {data, loading} = useQuery<GetTask, TaskQueryVariables>(GET_TASK, {
    variables: {id},
  });

  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  const [completeTask] = useMutation<GetTask, CompleteTaskVariables>(
    COMPLETE_TASK,
    {
      refetchQueries: [
        {query: GET_TASK, variables: {id}},
        {query: GET_TASK_DETAILS, variables: {id}},
        {query: GET_TASK_VARIABLES, variables: {id}},
      ],
    },
  );

  if (data === undefined || loading) {
    return null;
  }

  const {taskState, assignee} = data.task;

  const canCompleteTask =
    userData?.currentUser.username === assignee?.username &&
    taskState === TaskStates.Created;

  return (
    <>
      <Details />
      <Form
        onSubmit={() => {
          completeTask({variables: {id, variables: []}});
        }}
      >
        {({handleSubmit}) => (
          <StyledForm onSubmit={handleSubmit}>
            <Variables />
            {canCompleteTask && (
              <Footer>
                <PrimaryButton type="submit">Complete Task</PrimaryButton>
              </Footer>
            )}
          </StyledForm>
        )}
      </Form>
    </>
  );
};

export {Task};
