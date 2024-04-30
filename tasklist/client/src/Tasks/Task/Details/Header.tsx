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
