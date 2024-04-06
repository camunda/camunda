/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {DetailsSkeleton} from './Details/DetailsSkeleton';
import {useTask} from 'modules/queries/useTask';
import {isRequestError} from 'modules/request';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {decodeTaskOpenedRef} from 'modules/utils/reftags';
import {useTasks} from 'modules/queries/useTasks';
import {useAutoSelectNextTask} from 'modules/auto-select-task/useAutoSelectNextTask';
import {observer} from 'mobx-react-lite';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';

const CAMUNDA_FORMS_PREFIX = 'camunda-forms:bpmn:';

function isCamundaForms(formKey: NonNullable<TaskType['formKey']>): boolean {
  return formKey.startsWith(CAMUNDA_FORMS_PREFIX);
}

function getFormId(formKey: NonNullable<TaskType['formKey']>): string {
  return formKey.replace(CAMUNDA_FORMS_PREFIX, '');
}

const Task: React.FC = observer(() => {
  const filters = useTaskFilters();
  const {data, refetch: refetchAllTasks} = useTasks(filters);
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
  const {formKey, processDefinitionKey, formId, id: taskId} = task ?? {id};
  const {enabled: autoSelectNextTaskEnabled} = autoSelectNextTaskStore;
  const {goToTask: autoSelectGoToTask} = useAutoSelectNextTask();

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

    const customFilters = getStateLocally('customFilters')?.custom;

    tracking.track({
      eventName: 'task-completed',
      isCamundaForm: formKey ? isCamundaForms(formKey) : false,
      hasRemainingTasks,
      filter: filters.filter,
      customFilters: Object.keys(customFilters ?? {}),
      customFilterVariableCount: customFilters?.variables?.length ?? 0,
    });

    notificationsStore.displayNotification({
      kind: 'success',
      title: 'Task completed',
      isDismissable: true,
    });
  }

  async function handleSubmissionSuccess() {
    storeStateLocally('hasCompletedTask', true);

    if (autoSelectNextTaskEnabled) {
      const newTasks = (await refetchAllTasks()).data?.pages[0] ?? [];
      if (newTasks.length > 1 && newTasks[0].id === id) {
        autoSelectGoToTask(newTasks[1].id);
      } else if (newTasks.length > 0 && newTasks[0].id !== id) {
        autoSelectGoToTask(newTasks[0].id);
      } else {
        navigate({
          pathname: pages.initial,
          search: location.search,
        });
      }
    } else {
      refetchAllTasks();
      navigate({
        pathname: pages.initial,
        search: location.search,
      });
    }
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
});

Task.displayName = 'Task';

export {Task as Component};
