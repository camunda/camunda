/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Stack} from '@carbon/react';
import {CheckmarkFilled} from '@carbon/react/icons';
import {AssigneeTag} from 'Tasks/AssigneeTag';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {Restricted} from 'modules/components/Restricted';
import {CurrentUser, Task} from 'modules/types';
import {useAssignTask} from 'modules/mutations/useAssignTask';
import {useUnassignTask} from 'modules/mutations/useUnassignTask';
import {notificationsStore} from 'modules/stores/notifications';
import {tracking} from 'modules/tracking';
import {shouldFetchMore} from '../shouldFetchMore';
import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';
import {shouldDisplayNotification} from './shouldDisplayNotification';
import styles from './Header.module.scss';

const ASSIGNMENT_TOGGLE_LABEL = {
  assigning: 'Assigning...',
  unassigning: 'Unassigning...',
  assignmentSuccessful: 'Assignment successful',
  unassignmentSuccessful: 'Unassignment successful',
} as const;

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

  return (
    <header className={styles.header} title="Task details header">
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
              Completed
              {assignee ? (
                <>
                  {' '}
                  by
                  <span className={styles.taskAssignee} data-testid="assignee">
                    <AssigneeTag
                      currentUser={user}
                      assignee={assignee}
                      isShortFormat={true}
                    />
                  </span>
                </>
              ) : null}
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
          <Restricted scopes={['write']}>
            <span className={styles.assignButtonContainer}>
              <AssignButton
                id={id}
                assignee={assignee}
                onAssignmentError={onAssignmentError}
              />
            </span>
          </Restricted>
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
  const {mutateAsync: assignTask, isPending: assignIsPending} = useAssignTask();
  const {mutateAsync: unassignTask, isPending: unassignIsPending} =
    useUnassignTask();
  const isLoading = (assignIsPending || unassignIsPending) ?? false;

  const handleClick = async () => {
    try {
      if (isAssigned) {
        setAssignmentStatus('unassigning');
        await unassignTask(id);
        setAssignmentStatus('unassignmentSuccessful');
        tracking.track({eventName: 'task-unassigned'});
      } else {
        setAssignmentStatus('assigning');
        await assignTask(id);
        setAssignmentStatus('assignmentSuccessful');
        tracking.track({eventName: 'task-assigned'});
      }
    } catch (error) {
      const errorMessage = (error as Error).message ?? '';

      setAssignmentStatus('off');

      if (shouldDisplayNotification(errorMessage)) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: isAssigned
            ? 'Task could not be unassigned'
            : 'Task could not be assigned',
          subtitle: getTaskAssignmentChangeErrorMessage(errorMessage),
          isDismissable: true,
        });
      }

      // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getTaskAssignmentChangeErrorMessage
      if (shouldFetchMore(errorMessage)) {
        onAssignmentError();
      }
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
            : ASSIGNMENT_TOGGLE_LABEL[assignmentStatus],
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
      {isAssigned ? 'Unassign' : 'Assign to me'}
    </AsyncActionButton>
  );
};

export {Header};
