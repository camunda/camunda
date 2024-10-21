/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {
  useLocation,
  useNavigate,
  useOutletContext,
  useSearchParams,
} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {useCompleteTask} from 'modules/mutations/useCompleteTask';
import {useTranslation} from 'react-i18next';
import {pages, useTaskDetailsParams} from 'modules/routing';
import type {Variable} from 'modules/types';
import {tracking} from 'modules/tracking';
import {notificationsStore} from 'modules/stores/notifications';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import {isRequestError} from 'modules/request';
import {decodeTaskOpenedRef} from 'modules/utils/reftags';
import {useTasks} from 'modules/queries/useTasks';
import {useAutoSelectNextTask} from 'modules/auto-select-task/useAutoSelectNextTask';
import {autoSelectNextTaskStore} from 'modules/stores/autoSelectFirstTask';
import type {OutletContext} from './Details';
import {getCompleteTaskErrorMessage} from './getCompleteTaskErrorMessage';
import {shouldFetchMore} from './shouldFetchMore';
import {Variables} from './Variables';
import {FormJS} from './FormJS';

const Task: React.FC = observer(() => {
  const {
    task,
    currentUser,
    refetch: refetchTask,
  } = useOutletContext<OutletContext>();

  const filters = useTaskFilters();
  const {data: tasks, refetch: refetchAllTasks} = useTasks(filters);
  const {t} = useTranslation();
  const hasRemainingTasks = tasks.length > 0;

  const {id} = useTaskDetailsParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [searchParams, setSearchParams] = useSearchParams();
  const {mutateAsync: completeTask} = useCompleteTask();
  const {formKey, userTaskKey} = task;
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
  }, [searchParams, setSearchParams, userTaskKey]);

  async function handleSubmission(
    variables: Pick<Variable, 'name' | 'value'>[],
  ) {
    await completeTask({
      userTaskKey,
      variables,
    });

    const filter = new URLSearchParams(window.location.search).get('filter');
    const customFilters =
      filter === null ? null : getStateLocally('customFilters')?.[filter];

    tracking.track({
      eventName: 'task-completed',
      isCamundaForm: true,
      hasRemainingTasks,
      filter: filters.filter,
      customFilters: Object.keys(customFilters ?? {}),
      customFilterVariableCount: customFilters?.variables?.length ?? 0,
    });

    notificationsStore.displayNotification({
      kind: 'success',
      title: t('taskCompletedNotification'),
      isDismissable: true,
    });
  }

  async function handleSubmissionSuccess() {
    storeStateLocally('hasCompletedTask', true);

    if (autoSelectNextTaskEnabled) {
      const newTasks = (await refetchAllTasks()).data?.pages[0].items ?? [];
      const openTasks = newTasks.filter(({state}) => state === 'CREATED');
      if (openTasks.length > 1 && openTasks[0].userTaskKey === Number(id)) {
        autoSelectGoToTask(openTasks[1].userTaskKey);
      } else if (
        openTasks.length > 0 &&
        openTasks[0].userTaskKey !== Number(id)
      ) {
        autoSelectGoToTask(openTasks[0].userTaskKey);
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
      ? (error?.networkError?.message ?? error.message)
      : error.message;

    notificationsStore.displayNotification({
      kind: 'error',
      title: t('taskCouldNotBeCompletedNotification'),
      subtitle: getCompleteTaskErrorMessage(errorMessage),
      isDismissable: true,
    });

    // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getCompleteTaskErrorMessage
    if (shouldFetchMore(errorMessage)) {
      refetchTask();
    }
  }

  if (formKey !== undefined) {
    return (
      <FormJS
        key={task.userTaskKey}
        task={task}
        formKey={formKey}
        user={currentUser}
        onSubmit={handleSubmission}
        onSubmitSuccess={handleSubmissionSuccess}
        onSubmitFailure={handleSubmissionFailure}
      />
    );
  } else {
    return (
      <Variables
        key={userTaskKey}
        task={task}
        user={currentUser}
        onSubmit={handleSubmission}
        onSubmitSuccess={handleSubmissionSuccess}
        onSubmitFailure={handleSubmissionFailure}
      />
    );
  }
});

Task.displayName = 'Task';

export {Task as Component};
