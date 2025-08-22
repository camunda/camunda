/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate, useOutletContext} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {
  useCompleteTask,
  completionErrorMap,
} from 'v1/api/useCompleteTask.mutation';
import {useTranslation} from 'react-i18next';
import {pages, useTaskDetailsParams} from 'common/routing';
import type {Task as TaskType, Variable} from 'v1/api/types';
import {tracking} from 'common/tracking';
import {notificationsStore} from 'common/notifications/notifications.store';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {useTaskFilters} from 'v1/features/tasks/filters/useTaskFilters';
import {useTasks} from 'v1/api/useTasks.query';
import {useAutoSelectNextTask} from 'common/tasks/next-task/useAutoSelectNextTask';
import {autoSelectNextTaskStore} from 'common/tasks/next-task/autoSelectFirstTask';
import type {OutletContext} from 'v1/TaskDetailsLayout';
import {getCompleteTaskErrorMessage} from './getCompleteTaskErrorMessage';
import {Variables} from './Variables';
import {FormJS} from './FormJS';
import {useUploadDocuments} from 'common/api/useUploadDocuments.mutation';
import {ERRORS_THAT_SHOULD_FETCH_MORE} from './constants';

const CAMUNDA_FORMS_PREFIX = 'camunda-forms:bpmn:';

function isCamundaForms(formKey: NonNullable<TaskType['formKey']>): boolean {
  return formKey.startsWith(CAMUNDA_FORMS_PREFIX);
}

function getFormId(formKey: NonNullable<TaskType['formKey']>): string {
  return formKey.replace(CAMUNDA_FORMS_PREFIX, '');
}

const TaskDetails: React.FC = observer(() => {
  const {
    task,
    currentUser,
    refetch: refetchTask,
  } = useOutletContext<OutletContext>();

  const filters = useTaskFilters();
  const {data, refetch: refetchAllTasks} = useTasks(filters);
  const {t} = useTranslation();
  const tasks = data?.pages.flat() ?? [];
  const hasRemainingTasks = tasks.length > 0;
  const {id} = useTaskDetailsParams();
  const navigate = useNavigate();
  const location = useLocation();
  const {mutateAsync: completeTask} = useCompleteTask();
  const {mutateAsync: uploadDocuments} = useUploadDocuments();
  const {formKey, processDefinitionKey, formId, id: taskId} = task;
  const {enabled: autoSelectNextTaskEnabled} = autoSelectNextTaskStore;
  const {goToTask: autoSelectGoToTask} = useAutoSelectNextTask();

  async function handleSubmission(
    variables: Pick<Variable, 'name' | 'value'>[],
  ) {
    const completedTask = await completeTask({
      taskId,
      variables,
    });

    if (completedTask.taskState !== 'COMPLETED') {
      return;
    }

    const filter = new URLSearchParams(window.location.search).get('filter');
    const customFilters =
      filter === null ? null : getStateLocally('customFilters')?.[filter];

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
      title: t('taskCompletedNotification'),
      isDismissable: true,
    });
  }

  async function handleFileUpload(files: Map<string, File[]>) {
    if (files.size === 0) {
      return new Map();
    }

    return uploadDocuments({
      files,
    });
  }

  async function handleSubmissionSuccess() {
    storeStateLocally('hasCompletedTask', true);

    if (autoSelectNextTaskEnabled) {
      const newTasks = (await refetchAllTasks()).data?.pages[0] ?? [];
      const openTasks = newTasks.filter(
        ({taskState}) => taskState === 'CREATED',
      );
      if (openTasks.length > 1 && openTasks[0].id === id) {
        autoSelectGoToTask(openTasks[1].id);
      } else if (openTasks.length > 0 && openTasks[0].id !== id) {
        autoSelectGoToTask(openTasks[0].id);
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
    if (error.name === completionErrorMap.taskProcessingTimeout) {
      tracking.track({eventName: 'task-completion-delayed-notification'});
      notificationsStore.displayNotification({
        kind: 'info',
        title: t('taskCompletionDelayedInfoTitle'),
        subtitle: t('taskCompletionDelayedInfoSubtitle'),
        isDismissable: true,
      });
      return;
    }

    if (error.name === completionErrorMap.invalidState) {
      tracking.track({eventName: 'task-completion-rejected-notification'});
      notificationsStore.displayNotification({
        kind: 'error',
        title: t('taskCouldNotBeCompletedNotification'),
        subtitle: t('taskDetailsTaskCompletionRejectionErrorSubtitle'),
        isDismissable: true,
      });
    } else {
      notificationsStore.displayNotification({
        kind: 'error',
        title: t('taskCouldNotBeCompletedNotification'),
        subtitle: getCompleteTaskErrorMessage(error.message),
        isDismissable: true,
      });
    }

    if (ERRORS_THAT_SHOULD_FETCH_MORE.includes(error.name)) {
      refetchTask();
    }
  }

  const isDeployedForm = typeof formId === 'string';
  const isEmbeddedForm = typeof formKey === 'string' && task.isFormEmbedded;
  if (isEmbeddedForm || isDeployedForm) {
    return (
      <FormJS
        key={task.id}
        task={task}
        id={isEmbeddedForm ? getFormId(formKey) : formId!}
        user={currentUser}
        onSubmit={handleSubmission}
        onFileUpload={handleFileUpload}
        onSubmitSuccess={handleSubmissionSuccess}
        onSubmitFailure={handleSubmissionFailure}
        processDefinitionKey={processDefinitionKey!}
      />
    );
  } else {
    return (
      <Variables
        key={task.id}
        task={task}
        user={currentUser}
        onSubmit={handleSubmission}
        onSubmitSuccess={handleSubmissionSuccess}
        onSubmitFailure={handleSubmissionFailure}
      />
    );
  }
});

TaskDetails.displayName = 'Task';

export {TaskDetails as Component};
