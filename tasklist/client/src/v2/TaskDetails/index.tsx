/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLocation, useNavigate, useOutletContext} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {useCompleteTask} from 'v2/api/useCompleteTask.mutation';
import {useTranslation} from 'react-i18next';
import {pages, useTaskDetailsParams} from 'common/routing';
import {tracking} from 'common/tracking';
import {notificationsStore} from 'common/notifications/notifications.store';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {useTaskFilters} from 'v2/features/tasks/filters/useTaskFilters';
import {useTasks} from 'v2/api/useTasks.query';
import {useAutoSelectNextTask} from 'common/tasks/next-task/useAutoSelectNextTask';
import {autoSelectNextTaskStore} from 'common/tasks/next-task/autoSelectFirstTask';
import type {OutletContext} from 'v2/TaskDetailsLayout';
import {Variables} from './Variables';
import {FormJS} from './FormJS';
import {useUploadDocuments} from 'common/api/useUploadDocuments.mutation';
import {parseDenialReason} from 'v2/utils/parseDenialReason';
import {requestErrorSchema} from 'common/api/request';

const TaskDetails: React.FC = observer(() => {
  const {task, currentUser} = useOutletContext<OutletContext>();
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
  const {formKey, userTaskKey} = task;
  const {enabled: autoSelectNextTaskEnabled} = autoSelectNextTaskStore;
  const {goToTask: autoSelectGoToTask} = useAutoSelectNextTask();

  async function handleSubmission(variables: Record<string, unknown>) {
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
      const newTasks = (await refetchAllTasks()).data?.pages[0] ?? [];
      const openTasks = newTasks.filter(({state}) => state === 'CREATED');
      if (openTasks.length > 1 && openTasks[0].userTaskKey === id) {
        autoSelectGoToTask(openTasks[1].userTaskKey);
      } else if (openTasks.length > 0 && openTasks[0].userTaskKey !== id) {
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

  async function handleSubmissionFailure(error: unknown) {
    const {data: parsedError, success} = requestErrorSchema.safeParse(error);

    if (success && parsedError.variant === 'failed-response') {
      notificationsStore.displayNotification({
        kind: 'error',
        title: t('taskCouldNotBeCompletedNotification'),
        subtitle: parseDenialReason(
          await parsedError?.response?.json(),
          'completion',
        ),
        isDismissable: true,
      });
      if (parsedError?.response?.statusText.toLowerCase().includes('timeout')) {
        navigate({
          pathname: pages.initial,
          search: location.search,
        });
      }
      return;
    }

    notificationsStore.displayNotification({
      kind: 'error',
      title: t('taskCouldNotBeCompletedNotification'),
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

  if (formKey !== undefined) {
    return (
      <FormJS
        key={userTaskKey}
        task={task}
        user={currentUser}
        onSubmit={handleSubmission}
        onSubmitSuccess={handleSubmissionSuccess}
        onSubmitFailure={handleSubmissionFailure}
        onFileUpload={handleFileUpload}
      />
    );
  }

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
});

TaskDetails.displayName = 'Task';

export {TaskDetails as Component};
