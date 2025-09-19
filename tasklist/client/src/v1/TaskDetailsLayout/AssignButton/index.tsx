/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t as _t} from 'i18next';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import type {Task} from 'v1/api/types';
import {AsyncActionButton} from 'common/components/AsyncActionButton';
import {notificationsStore} from 'common/notifications/notifications.store';
import {tracking} from 'common/tracking';
import {useAssignTask} from 'v1/api/useAssignTask.mutation';
import {useUnassignTask} from 'v1/api/useUnassignTask.mutation';
import {usePollForAssignmentResult} from 'v1/api/usePollForAssignmentResult.mutation';
import {requestErrorSchema} from 'common/api/request';

type AssignmentStatus =
  | 'off'
  | 'assigning'
  | 'unassigning'
  | 'assignmentSuccessful'
  | 'unassignmentSuccessful';

const getAssignmentToggleLabels = () =>
  ({
    assigning: _t('taskHeaderAssigning'),
    unassigning: _t('taskHeaderUnassigning'),
    assignmentSuccessful: _t('taskHeaderAssignmentSuccessful'),
    unassignmentSuccessful: _t('taskHeaderUnassignmentSuccessful'),
  }) as Record<AssignmentStatus, string>;

type Props = {
  id: string;
  assignee: string | null;
  taskState: Task['taskState'];
};

const AssignButton: React.FC<Props> = ({id, assignee, taskState}) => {
  const isAssigned = typeof assignee === 'string' && taskState !== 'ASSIGNING';
  const {t} = useTranslation();
  const [assignmentStatus, setAssignmentStatus] =
    useState<AssignmentStatus>('off');
  const {mutateAsync: pollForAssignmentResult} = usePollForAssignmentResult();
  const {mutateAsync: assignTask, isPending: assignIsPending} = useAssignTask();
  const {mutateAsync: unassignTask, isPending: unassignIsPending} =
    useUnassignTask();
  const isLoading =
    (assignIsPending || unassignIsPending || taskState === 'ASSIGNING') ??
    false;

  const effectiveAssignmentStatus =
    taskState === 'ASSIGNING' ? 'assigning' : assignmentStatus;

  function getAsyncActionButtonStatus() {
    if (isLoading || effectiveAssignmentStatus !== 'off') {
      const ACTIVE_STATES: AssignmentStatus[] = ['assigning', 'unassigning'];

      return ACTIVE_STATES.includes(effectiveAssignmentStatus) ||
        taskState === 'ASSIGNING'
        ? 'active'
        : 'finished';
    }

    return 'inactive';
  }

  const handleAssignmentClick = async () => {
    try {
      setAssignmentStatus('assigning');
      await assignTask(id);

      await pollForAssignmentResult({
        taskId: id,
        wasAssigned: false,
      });

      setAssignmentStatus('assignmentSuccessful');
      tracking.track({eventName: 'task-assigned'});
    } catch (error) {
      const {data: parsedError, success} = requestErrorSchema.safeParse(error);

      if (success && parsedError.variant === 'failed-response') {
        const errorData = await parsedError.response.json();

        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskAssignmentError'),
          isDismissable: true,
        });

        if (errorData.title !== 'DEADLINE_EXCEEDED') {
          setAssignmentStatus('off');
        }
        return;
      }

      notificationsStore.displayNotification({
        kind: 'error',
        title: t('taskDetailsTaskAssignmentError'),
        isDismissable: true,
      });
      setAssignmentStatus('off');
    }
  };

  const handleUnassignmentClick = async () => {
    try {
      setAssignmentStatus('unassigning');
      await unassignTask(id);
      await pollForAssignmentResult({
        taskId: id,
        wasAssigned: true,
      });
      setAssignmentStatus('unassignmentSuccessful');
      tracking.track({eventName: 'task-unassigned'});
    } catch (error) {
      const {data: parsedError, success} = requestErrorSchema.safeParse(error);

      if (success && parsedError.variant === 'failed-response') {
        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskUnassignmentError'),
          isDismissable: true,
        });
        return;
      }

      notificationsStore.displayNotification({
        kind: 'error',
        title: t('taskDetailsTaskUnassignmentError'),
        isDismissable: true,
      });

      setAssignmentStatus('off');
    }
  };

  const handleClick = async () => {
    if (isAssigned) {
      handleUnassignmentClick();
    } else {
      handleAssignmentClick();
    }
  };

  return (
    <AsyncActionButton
      inlineLoadingProps={{
        description:
          effectiveAssignmentStatus === 'off'
            ? undefined
            : getAssignmentToggleLabels()[effectiveAssignmentStatus],
        'aria-live': ['assigning', 'unassigning'].includes(
          effectiveAssignmentStatus,
        )
          ? 'assertive'
          : 'polite',
        onSuccess: () => {
          setAssignmentStatus('off');
        },
      }}
      buttonProps={{
        kind: isAssigned ? 'ghost' : 'primary',
        size: 'sm',
        type: 'button',
        onClick: handleClick,
        disabled: isLoading,
        autoFocus: true,
        id: 'main-content',
      }}
      status={getAsyncActionButtonStatus()}
    >
      {isAssigned ? t('taskDetailsUnassign') : t('taskDetailsAssignToMe')}
    </AsyncActionButton>
  );
};

export {AssignButton};
