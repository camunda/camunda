/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useParams} from 'react-router-dom';
import {useQuery, useMutation} from '@apollo/react-hooks';
import {Form} from 'react-final-form';

import {useHistory} from 'react-router-dom';

import {TaskStates} from 'modules/constants/taskStates';
import {GET_TASK, GetTask, TaskQueryVariables} from 'modules/queries/get-task';
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
import {Pages} from 'modules/constants/pages';

const Task: React.FC = () => {
  const {id} = useParams();
  const history = useHistory();

  interface FormValues {
    [name: string]: string;
  }

  const {data, loading} = useQuery<GetTask, TaskQueryVariables>(GET_TASK, {
    variables: {id},
  });
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const [completeTask] = useMutation<GetTask, CompleteTaskVariables>(
    COMPLETE_TASK,
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
      <Form<FormValues>
        onSubmit={async (values, form) => {
          const {dirtyFields} = form.getState();

          await completeTask({
            variables: {
              id,
              variables: Object.keys(dirtyFields).map((name) => ({
                name,
                value: values[name],
              })),
            },
          });
          history.push({
            pathname: Pages.Initial(),
            search: history.location.search,
          });
        }}
      >
        {({form, handleSubmit}) => {
          return (
            <StyledForm onSubmit={handleSubmit} hasFooter={canCompleteTask}>
              <Variables canEdit={canCompleteTask} />
              {canCompleteTask && (
                <Footer>
                  <PrimaryButton
                    type="submit"
                    disabled={form.getState().submitting}
                  >
                    Complete Task
                  </PrimaryButton>
                </Footer>
              )}
            </StyledForm>
          );
        }}
      </Form>
    </>
  );
};

export {Task};
