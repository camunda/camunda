/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {useParams, useLocation, useNavigate} from 'react-router-dom';
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
import {Container} from './styled';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {Pages} from 'modules/constants/pages';
import {Task as TaskType, Variable} from 'modules/types';
import {FormJS} from './FormJS';
import {tracking} from 'modules/tracking';
import {notificationsStore} from 'modules/stores/notifications';
import {Skeleton} from './Skeleton';
import {storeStateLocally} from 'modules/utils/localStorage';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';

const CAMUNDA_FORMS_PREFIX = 'camunda-forms:bpmn:';

function isCamundaForms(formKey: NonNullable<TaskType['formKey']>): boolean {
  return formKey.startsWith(CAMUNDA_FORMS_PREFIX);
}

function getFormId(formKey: NonNullable<TaskType['formKey']>): string {
  return formKey.replace(CAMUNDA_FORMS_PREFIX, '');
}

type Props = {
  hasRemainingTasks: boolean;
  onCompleted?: () => void;
};

const Task: React.FC<Props> = ({hasRemainingTasks, onCompleted}) => {
  const {id = ''} = useParams<'id'>();
  const navigate = useNavigate();
  const location = useLocation();
  const {fetchMore, data, loading} = useTask(id);

  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);

  const [completeTask] = useMutation<GetTask, CompleteTaskVariables>(
    COMPLETE_TASK,
    {
      onCompleted,
    },
  );
  const {filter} = useTaskFilters();
  const {formKey, processDefinitionId, id: taskId} = data?.task ?? {};

  useEffect(() => {
    tracking.track({
      eventName: 'task-opened',
    });
  }, [taskId]);

  async function handleSubmission(
    variables: Pick<Variable, 'name' | 'value'>[],
  ) {
    await completeTask({
      variables: {
        id,
        variables,
      },
    });

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
      pathname: Pages.Initial(),
      search: location.search,
    });
  }

  function handleSubmissionFailure(error: Error) {
    const errorMessage = error.message;
    notificationsStore.displayNotification({
      kind: 'error',
      title: 'Task could not be completed',
      subtitle: getCompleteTaskErrorMessage(errorMessage),
      isDismissable: true,
    });

    // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getCompleteTaskErrorMessage
    if (shouldFetchMore(errorMessage)) {
      fetchMore({variables: {id}});
    }
  }

  return (
    <Container>
      {loading && <Skeleton data-testid="details-skeleton" />}
      {data !== undefined && userData !== undefined && (
        <>
          <Details />
          {typeof formKey === 'string' &&
          typeof processDefinitionId === 'string' &&
          isCamundaForms(formKey) ? (
            <FormJS
              key={data.task.id}
              task={data.task}
              id={getFormId(formKey)}
              user={userData.currentUser}
              onSubmit={handleSubmission}
              onSubmitSuccess={handleSubmissionSuccess}
              onSubmitFailure={handleSubmissionFailure}
              processDefinitionId={processDefinitionId}
            />
          ) : (
            <Variables
              key={data.task.id}
              task={data.task}
              user={userData.currentUser}
              onSubmit={handleSubmission}
              onSubmitSuccess={handleSubmissionSuccess}
              onSubmitFailure={handleSubmissionFailure}
            />
          )}
        </>
      )}
    </Container>
  );
};

export {Task};
