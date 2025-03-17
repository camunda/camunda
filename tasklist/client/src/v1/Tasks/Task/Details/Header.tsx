/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {t as _t} from 'i18next';
import {useTranslation} from 'react-i18next';
import {Stack} from '@carbon/react';
import {CheckmarkFilled} from '@carbon/react/icons';
import {AssigneeTag} from 'v1/Tasks/AssigneeTag';
import {AsyncActionButton} from 'common/components/AsyncActionButton';
import type {Task} from 'v1/api/types';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/identity';
import {useAssignTask, assignmentErrorMap} from 'v1/api/useAssignTask.mutation';
import {
  useUnassignTask,
  unassignmentErrorMap,
} from 'v1/api/useUnassignTask.mutation';
import {notificationsStore} from 'common/notifications/notifications.store';
import {tracking} from 'common/tracking';
import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';
import {shouldDisplayNotification} from './shouldDisplayNotification';
import styles from './Header.module.scss';
import {ERRORS_THAT_SHOULD_FETCH_MORE} from '../constants';

const getAssignmentToggleLabels = () =>
  ({
    assigning: _t('taskHeaderAssigning'),
    unassigning: _t('taskHeaderUnassigning'),
    assignmentSuccessful: _t('taskHeaderAssignmentSuccessful'),
    unassignmentSuccessful: _t('taskHeaderUnassignmentSuccessful'),
  }) as Record<AssignmentStatus, string>;

type AssignmentStatus =
  | 'off'
  | 'assigning'
  | 'unassigning'
  | 'assignmentSuccessful'
  | 'unassignmentSuccessful';

type Props = {
  task: Task;
  user: CurrentUser;
  onAssignmentError: () => void;
};

const Header: React.FC<Props> = ({task, user, onAssignmentError}) => {
  const {id, name, processName, assignee, taskState} = task;
  const {t} = useTranslation();

  return (
    <header className={styles.header} title={t('taskDetailsHeader')}>
      <div className={styles.headerLeftContainer}>
        <span className={styles.taskName}>{name}</span>
        <span className={styles.processName}>{processName}</span>
      </div>
      <div className={styles.headerRightContainer}>
        {taskState === 'COMPLETED' ? (
          <span
            className={styles.taskStatus}
            data-testid="completion-label"
            title="Completed by"
          >
            <Stack
              className={styles.alignItemsCenter}
              orientation="horizontal"
              gap={2}
            >
              <CheckmarkFilled size={16} color="green" />
              {assignee ? (
                <>
                  {t('taskDetailsTaskCompletedBy') + ' '}
                  <span className={styles.taskAssignee} data-testid="assignee">
                    <AssigneeTag
                      currentUser={user}
                      assignee={assignee}
                      isShortFormat={true}
                    />
                  </span>
                </>
              ) : (
                t('taskAssignmentStatusCompleted')
              )}
            </Stack>
          </span>
        ) : (
          <span className={styles.taskAssignee} data-testid="assignee">
            <AssigneeTag
              currentUser={user}
              assignee={assignee}
              isShortFormat={false}
            />
          </span>
        )}
        {taskState === 'CREATED' && (
          <span className={styles.assignButtonContainer}>
            <AssignButton
              id={id}
              assignee={assignee}
              onAssignmentError={onAssignmentError}
            />
          </span>
        )}
      </div>
    </header>
  );
};

const AssignButton: React.FC<{
  id: string;
  assignee: string | null;
  onAssignmentError: () => void;
}> = ({id, assignee, onAssignmentError}) => {
  const isAssigned = assignee !== null;
  const [assignmentStatus, setAssignmentStatus] =
    useState<AssignmentStatus>('off');
  const {t} = useTranslation();
  const {mutateAsync: assignTask, isPending: assignIsPending} = useAssignTask();
  const {mutateAsync: unassignTask, isPending: unassignIsPending} =
    useUnassignTask();
  const isLoading = (assignIsPending || unassignIsPending) ?? false;

  const handleAssignmentClick = async () => {
    try {
      setAssignmentStatus('assigning');
      await assignTask(id);
      setAssignmentStatus('assignmentSuccessful');
      tracking.track({eventName: 'task-assigned'});
    } catch (error) {
      if (!(error instanceof Error)) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskAssignmentError'),
          isDismissable: true,
        });

        setAssignmentStatus('off');
        return;
      }

      if (error.name === assignmentErrorMap.taskProcessingTimeout) {
        tracking.track({
          eventName: 'task-assignment-delayed-notification',
        });
        notificationsStore.displayNotification({
          kind: 'info',
          title: t('taskDetailsAssignmentDelayInfoTitle'),
          subtitle: t('taskDetailsAssignmentDelayInfoSubtitle'),
          isDismissable: true,
        });
        return;
      }

      setAssignmentStatus('off');
      if (error.name === assignmentErrorMap.invalidState) {
        tracking.track({
          eventName: 'task-assignment-rejected-notification',
        });
        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskAssignmentError'),
          subtitle: t('taskDetailsTaskAssignmentRejectionErrorSubtitle'),
          isDismissable: true,
        });
      } else if (shouldDisplayNotification(error.message)) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskAssignmentError'),
          subtitle: getTaskAssignmentChangeErrorMessage(error.message),
          isDismissable: true,
        });
      }

      if (ERRORS_THAT_SHOULD_FETCH_MORE.includes(error.name)) {
        onAssignmentError();
      }
    }
  };

  const handleUnassignmentClick = async () => {
    try {
      setAssignmentStatus('unassigning');
      await unassignTask(id);
      setAssignmentStatus('unassignmentSuccessful');
      tracking.track({eventName: 'task-unassigned'});
    } catch (error) {
      if (!(error instanceof Error)) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskUnassignmentError'),
          isDismissable: true,
        });

        setAssignmentStatus('off');
        return;
      }

      if (error.name === unassignmentErrorMap.taskProcessingTimeout) {
        tracking.track({
          eventName: 'task-unassignment-delayed-notification',
        });
        notificationsStore.displayNotification({
          kind: 'info',
          title: t('taskDetailsUnassignmentDelayInfoTitle'),
          subtitle: t('taskDetailsUnassignmentDelayInfoSubtitle'),
          isDismissable: true,
        });
        return;
      }

      setAssignmentStatus('off');
      if (
        error.name === unassignmentErrorMap.taskNotAssigned ||
        error.name === unassignmentErrorMap.taskNotAssignedToCurrentUser
      ) {
        tracking.track({
          eventName: 'task-unassignment-rejected-notification',
        });
        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskUnassignmentError'),
          subtitle: t('taskDetailsTaskUnassignmentRejectionErrorSubtitle'),

          isDismissable: true,
        });
      } else if (shouldDisplayNotification(error.message)) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: t('taskDetailsTaskUnassignmentError'),
          subtitle: getTaskAssignmentChangeErrorMessage(error.message),
          isDismissable: true,
        });
      }

      if (ERRORS_THAT_SHOULD_FETCH_MORE.includes(error.name)) {
        onAssignmentError();
      }
    }
  };

  const handleClick = async () => {
    if (isAssigned) {
      handleUnassignmentClick();
    } else {
      handleAssignmentClick();
    }
  };

  function getAsyncActionButtonStatus() {
    if (isLoading || assignmentStatus !== 'off') {
      const ACTIVE_STATES: AssignmentStatus[] = ['assigning', 'unassigning'];

      return ACTIVE_STATES.includes(assignmentStatus) ? 'active' : 'finished';
    }

    return 'inactive';
  }

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

export {Header};
