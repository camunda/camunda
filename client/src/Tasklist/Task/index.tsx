/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useParams, useLocation, useHistory} from 'react-router-dom';
import {useQuery, useMutation} from '@apollo/client';
import {GetTask, useTask} from 'modules/queries/get-task';
import {
  COMPLETE_TASK,
  CompleteTaskVariables,
} from 'modules/mutations/complete-task';
import {getCompleteTaskErrorMessage} from './getCompleteTaskErrorMessage';
import {shouldFetchMore} from './shouldFetchMore';
import {Variables} from './Variables';
import {Details} from './Details';
import {Container, LoadingOverlay} from './styled';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {Pages} from 'modules/constants/pages';
import {Task as TaskType, Variable} from 'modules/types';
import {GetTasks, GET_TASKS} from 'modules/queries/get-tasks';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {FilterValues} from 'modules/constants/filterValues';
import {useNotifications} from 'modules/notifications';
import {FormJS} from './FormJS';

import {
  MAX_TASKS_PER_REQUEST,
  MAX_TASKS_DISPLAYED,
} from 'modules/constants/tasks';
import {getSortValues} from './getSortValues';

const CAMUNDA_FORMS_PREFIX = 'camunda-forms:bpmn:';

function isCamundaForms(formKey: NonNullable<TaskType['formKey']>): boolean {
  return formKey.startsWith(CAMUNDA_FORMS_PREFIX);
}

function getFormId(formKey: NonNullable<TaskType['formKey']>): string {
  return formKey.replace(CAMUNDA_FORMS_PREFIX, '');
}

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
  const {formKey, processDefinitionId} = data?.task ?? {};

  async function handleSubmission(variables: Variable[]) {
    try {
      await completeTask({
        variables: {
          id,
          variables,
        },
      });

      notifications.displayNotification('success', {
        headline: 'Task completed',
      });

      const searchParams = new URLSearchParams(location.search);
      const gseUrl = searchParams.get('gseUrl');

      if (gseUrl !== null && !notifications.isGseNotificationVisible) {
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
  }

  return (
    <Container>
      {loading && id !== undefined && (
        <LoadingOverlay data-testid="details-overlay" />
      )}
      {data !== undefined && (
        <>
          <Details />
          {typeof formKey === 'string' &&
          typeof processDefinitionId === 'string' &&
          isCamundaForms(formKey) ? (
            <FormJS
              key={id}
              task={data.task}
              id={getFormId(formKey)}
              onSubmit={handleSubmission}
              processDefinitionId={processDefinitionId}
            />
          ) : (
            <Variables key={id} task={data.task} onSubmit={handleSubmission} />
          )}
        </>
      )}
    </Container>
  );
};

export {Task};
