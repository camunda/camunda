/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t as _t} from 'i18next';
import {AsyncActionButton} from 'common/components/AsyncActionButton';
import {notificationsStore} from 'common/notifications/notifications.store';
import {tracking} from 'common/tracking';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {useAssignTask} from 'v2/api/useAssignTask.mutation';
import {useUnassignTask} from 'v2/api/useUnassignTask.mutation';
import {usePollForAssignmentResult} from 'v2/api/usePollForAssignmentResult.mutation';

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
  assignee: string | undefined;
  currentUser: string;
};

const AssignButton: React.FC<Props> = ({id, assignee, currentUser}) => {
  const isAssigned = typeof assignee === 'string';
  const {t} = useTranslation();
  const [assignmentStatus, setAssignmentStatus] =
    useState<AssignmentStatus>('off');
  const {mutateAsync: pollForAssignmentResult} = usePollForAssignmentResult();
  const {mutateAsync: assignTask, isPending: assignIsPending} = useAssignTask();
  const {mutateAsync: unassignTask, isPending: unassignIsPending} =
    useUnassignTask();
  const isLoading = (assignIsPending || unassignIsPending) ?? false;

  function getAsyncActionButtonStatus() {
    if (isLoading || assignmentStatus !== 'off') {
      const ACTIVE_STATES: AssignmentStatus[] = ['assigning', 'unassigning'];

      return ACTIVE_STATES.includes(assignmentStatus) ? 'active' : 'finished';
    }

    return 'inactive';
  }

  const handleAssignmentClick = async () => {
    try {
      setAssignmentStatus('assigning');
      await assignTask({
        userTaskKey: id,
        assignee: currentUser,
      });
      await pollForAssignmentResult({
        userTaskKey: id,
        wasAssigned: false,
      });
      setAssignmentStatus('assignmentSuccessful');
      tracking.track({eventName: 'task-assigned'});
    } catch {
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
        userTaskKey: id,
        wasAssigned: true,
      });
      setAssignmentStatus('unassignmentSuccessful');
      tracking.track({eventName: 'task-unassigned'});
    } catch {
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
          assignmentStatus === 'off'
            ? undefined
            : getAssignmentToggleLabels()[assignmentStatus],
        'aria-live': ['assigning', 'unassigning'].includes(assignmentStatus)
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
