/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {useLocation, useNavigate, useSearchParams} from 'react-router-dom';
import {useCompleteTask} from 'modules/mutations/useCompleteTask';
import {getCompleteTaskErrorMessage} from './getCompleteTaskErrorMessage';
import {shouldFetchMore} from './shouldFetchMore';
import {Variables} from './Variables';
import {Details} from './Details';
import {pages, useTaskDetailsParams} from 'modules/routing';
import {Task as TaskType, Variable} from 'modules/types';
import {FormJS} from './FormJS';
import {tracking} from 'modules/tracking';
import {notificationsStore} from 'modules/stores/notifications';
import {storeStateLocally} from 'modules/utils/localStorage';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {DetailsSkeleton} from './Details/DetailsSkeleton';
import {useTask} from 'modules/queries/useTask';
import {isRequestError} from 'modules/request';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {decodeTaskOpenedRef} from 'modules/utils/reftags';
import {useTasks} from 'modules/queries/useTasks';

const CAMUNDA_FORMS_PREFIX = 'camunda-forms:bpmn:';

function isCamundaForms(formKey: NonNullable<TaskType['formKey']>): boolean {
  return formKey.startsWith(CAMUNDA_FORMS_PREFIX);
}

function getFormId(formKey: NonNullable<TaskType['formKey']>): string {
  return formKey.replace(CAMUNDA_FORMS_PREFIX, '');
}

const Task: React.FC = () => {
  const {data, refetch: onCompleted} = useTasks();
  const tasks = data?.pages.flat() ?? [];
  const hasRemainingTasks = tasks.length > 0;
  const {id} = useTaskDetailsParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const {data: task, refetch} = useTask(id, {
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });
  const {data: currentUser} = useCurrentUser();
  const {mutateAsync: completeTask} = useCompleteTask();
  const {filter} = useTaskFilters();
  const {formKey, processDefinitionKey, formId, id: taskId} = task ?? {id};

  useEffect(() => {
    const search = new URLSearchParams(searchParams);
    const ref = search.get('ref');
    if (search.has('ref')) {
      search.delete('ref');
      setSearchParams(search, {replace: true});
    }

    const taskOpenedRef = decodeTaskOpenedRef(ref);
    tracking.track({
      eventName: 'task-opened',
      ...(taskOpenedRef ?? {}),
    });
  }, [searchParams, setSearchParams, taskId]);

  async function handleSubmission(
    variables: Pick<Variable, 'name' | 'value'>[],
  ) {
    await completeTask({
      taskId,
      variables,
    });

    onCompleted?.();

    tracking.track({
      eventName: 'task-completed',
      isCamundaForm: formKey ? isCamundaForms(formKey) : false,
      hasRemainingTasks,
      filter,
    });

    notificationsStore.displayNotification({
      kind: 'success',
      title: 'Task completed',
      isDismissable: true,
    });
  }

  function handleSubmissionSuccess() {
    storeStateLocally('hasCompletedTask', true);
    navigate({
      pathname: pages.initial,
      search: location.search,
    });
  }

  function handleSubmissionFailure(error: Error) {
    const errorMessage = isRequestError(error)
      ? error?.networkError?.message ?? error.message
      : error.message;

    notificationsStore.displayNotification({
      kind: 'error',
      title: 'Task could not be completed',
      subtitle: getCompleteTaskErrorMessage(errorMessage),
      isDismissable: true,
    });

    // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getCompleteTaskErrorMessage
    if (shouldFetchMore(errorMessage)) {
      refetch();
    }
  }

  if (task === undefined || currentUser === undefined) {
    return <DetailsSkeleton data-testid="details-skeleton" />;
  }

  const isDeployedForm = typeof formId === 'string';
  const isEmbeddedForm = typeof formKey === 'string' && !isDeployedForm;

  return (
    <Details task={task} user={currentUser} onAssignmentError={refetch}>
      {isEmbeddedForm || isDeployedForm ? (
        <FormJS
          key={task.id}
          task={task}
          id={isEmbeddedForm ? getFormId(formKey) : formId!}
          user={currentUser}
          onSubmit={handleSubmission}
          onSubmitSuccess={handleSubmissionSuccess}
          onSubmitFailure={handleSubmissionFailure}
          processDefinitionKey={processDefinitionKey!}
        />
      ) : (
        <Variables
          key={task.id}
          task={task}
          user={currentUser}
          onSubmit={handleSubmission}
          onSubmitSuccess={handleSubmissionSuccess}
          onSubmitFailure={handleSubmissionFailure}
        />
      )}
    </Details>
  );
};

Task.displayName = 'Task';

export {Task as Component};
