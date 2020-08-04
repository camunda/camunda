/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useParams} from 'react-router-dom';
import {useQuery, useMutation} from '@apollo/react-hooks';
import {Form} from 'react-final-form';
import {get, intersection} from 'lodash';
import arrayMutators from 'final-form-arrays';

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
import {Variable} from 'modules/types';
import {GET_TASKS} from 'modules/queries/get-tasks';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {useLocation} from 'react-router-dom';
import {FilterValues} from 'modules/constants/filterValues';

const Task: React.FC = () => {
  const {id} = useParams();
  const history = useHistory();
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;

  const {data, loading} = useQuery<GetTask, TaskQueryVariables>(GET_TASK, {
    variables: {id},
  });
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const [completeTask] = useMutation<GetTask, CompleteTaskVariables>(
    COMPLETE_TASK,
    {
      refetchQueries: [
        {
          query: GET_TASKS,
          variables: getQueryVariables(filter, {
            username: userData?.currentUser.username,
          }),
        },
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
        mutators={{...arrayMutators}}
        onSubmit={async (values, form) => {
          const {dirtyFields, initialValues = []} = form.getState();

          const existingVariables: ReadonlyArray<Variable> = intersection(
            Object.keys(initialValues),
            Object.keys(dirtyFields),
          ).map((name) => ({
            name,
            value: values[name],
          }));

          const newVariables: ReadonlyArray<Variable> =
            get(values, 'new-variables') || [];

          await completeTask({
            variables: {
              id,
              variables: [...existingVariables, ...newVariables],
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
