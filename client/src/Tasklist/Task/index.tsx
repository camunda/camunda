/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useParams} from 'react-router-dom';
import {useQuery, useMutation} from '@apollo/client';
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
import {getCompleteTaskErrorMessage} from './getCompleteTaskErrorMessage';
import {shouldFetchMore} from './shouldFetchMore';
import {Button} from 'modules/components/Button';

import {Variables} from './Variables';
import {Details} from './Details';
import {Footer, Form as StyledForm, Container, LoadingOverlay} from './styled';
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
import {useNotifications} from 'modules/notifications';

const Task: React.FC = () => {
  const {id} = useParams<{id: string}>();
  const history = useHistory();
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;

  const {data, loading, fetchMore} = useQuery<GetTask, TaskQueryVariables>(
    GET_TASK,
    {
      variables: {id},
    },
  );
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
  const notifications = useNotifications();

  if (loading && id !== undefined) {
    return <LoadingOverlay data-testid="details-overlay" />;
  }

  if (data === undefined) {
    return null;
  }

  const {taskState, assignee} = data.task;
  const canCompleteTask =
    userData?.currentUser.username === assignee?.username &&
    taskState === TaskStates.Created;

  return (
    <Container>
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

          try {
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
          } catch (error) {
            notifications.displayNotification('error', {
              headline: 'Task could not be completed',
              description: getCompleteTaskErrorMessage(error.message),
            });

            // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getCompleteTaskErrorMessage
            if (shouldFetchMore(error.message)) {
              fetchMore({variables: {id}});
            }
          }
        }}
      >
        {({form, handleSubmit}) => {
          return (
            <StyledForm onSubmit={handleSubmit} hasFooter={canCompleteTask}>
              <Variables canEdit={canCompleteTask} />
              {canCompleteTask && (
                <Footer>
                  <Button
                    type="submit"
                    disabled={
                      form.getState().submitting ||
                      form.getState().hasValidationErrors
                    }
                  >
                    Complete Task
                  </Button>
                </Footer>
              )}
            </StyledForm>
          );
        }}
      </Form>
    </Container>
  );
};

export {Task};
