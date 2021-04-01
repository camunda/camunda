/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useParams, useLocation, useHistory} from 'react-router-dom';
import {useQuery, useMutation} from '@apollo/client';
import {Form} from 'react-final-form';
import {get, intersection} from 'lodash';
import arrayMutators from 'final-form-arrays';

import {TaskStates} from 'modules/constants/taskStates';
import {GetTask, useTask} from 'modules/queries/get-task';
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
import {GetTasks, GET_TASKS} from 'modules/queries/get-tasks';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {FilterValues} from 'modules/constants/filterValues';
import {useNotifications} from 'modules/notifications';
import {getVariableFieldName} from './getVariableFieldName';
import {
  MAX_TASKS_PER_REQUEST,
  MAX_TASKS_DISPLAYED,
} from 'modules/constants/tasks';
import {getSortValues} from './getSortValues';
import {createVariableFieldName} from './Variables/createVariableFieldName';

type FormType = {
  [key: string]: string;
} & {
  newVariables?: Variable[];
};

const Task: React.FC = () => {
  const {id} = useParams<{id: string}>();
  const history = useHistory();
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;

  const {data: dataFromCache} = useQuery<GetTasks>(GET_TASKS, {
    fetchPolicy: 'cache-only',
  });
  const currentTaskCount = dataFromCache?.tasks?.length ?? 0;

  const {data, loading, fetchMore} = useTask(id);
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const [completeTask] = useMutation<GetTask, CompleteTaskVariables>(
    COMPLETE_TASK,
    {
      refetchQueries: [
        {
          query: GET_TASKS,
          variables: {
            ...getQueryVariables(filter, {
              username: userData?.currentUser.username,
              pageSize:
                currentTaskCount <= MAX_TASKS_PER_REQUEST
                  ? MAX_TASKS_PER_REQUEST
                  : MAX_TASKS_DISPLAYED,
              searchAfterOrEqual: getSortValues(dataFromCache?.tasks),
            }),
            isRunAfterMutation: true,
          },
        },
      ],
    },
  );
  const notifications = useNotifications();
  const {taskState, assignee} = data?.task ?? {};

  const canCompleteTask =
    userData?.currentUser.username === assignee?.username &&
    taskState === TaskStates.Created;

  return (
    <Container>
      {loading && id !== undefined && (
        <LoadingOverlay data-testid="details-overlay" />
      )}
      {data !== undefined && (
        <>
          <Details />
          <Form<FormType>
            mutators={{...arrayMutators}}
            validate={(values) => {
              const {newVariables} = values;

              if (
                newVariables !== undefined &&
                newVariables.some((variable) => variable !== undefined)
              ) {
                return {
                  newVariables: newVariables.map((variable, index) => {
                    if (variable === undefined) {
                      return undefined;
                    }

                    const {name} = variable;

                    if (values.hasOwnProperty(createVariableFieldName(name))) {
                      return {name: 'Name must be unique'};
                    }

                    if (
                      newVariables.filter((variable) => variable?.name === name)
                        .length <= 1
                    ) {
                      return undefined;
                    }

                    if (
                      newVariables.findIndex(
                        (variable) => variable?.name === name,
                      ) === index
                    ) {
                      return undefined;
                    }

                    return {name: 'Name must be unique'};
                  }),
                };
              }

              return {};
            }}
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
                get(values, 'newVariables') || [];

              try {
                await completeTask({
                  variables: {
                    id,
                    variables: [
                      ...existingVariables.map((variable) => ({
                        ...variable,
                        name: getVariableFieldName(variable.name),
                      })),
                      ...newVariables,
                    ],
                  },
                });

                notifications.displayNotification('success', {
                  headline: 'Task completed',
                });

                const searchParams = new URLSearchParams(location.search);
                const gseUrl = searchParams.get('gseUrl');

                if (
                  gseUrl !== null &&
                  !notifications.isGseNotificationVisible
                ) {
                  notifications.displayNotification('info', {
                    headline: 'To continue to getting started, go back to',
                    isDismissable: false,
                    isGseNotification: true,
                    navigation: {
                      label: 'Cloud',
                      navigationHandler: () => {
                        window.location.href = gseUrl;
                      },
                    },
                  });
                }

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
        </>
      )}
    </Container>
  );
};

export {Task};
