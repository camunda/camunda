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

import {formatDate} from 'modules/utils/formatDate';
import {shouldFetchMore} from './shouldFetchMore';
import {shouldDisplayNotification} from './shouldDisplayNotification';
import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';
import {Restricted} from 'modules/components/Restricted';
import {tracking} from 'modules/tracking';
import {useState} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {TaskDetailsRow} from 'modules/components/TaskDetailsLayout';
import {
  ContainedList,
  ContainedListItem,
  Section,
  Stack,
  Tag,
} from '@carbon/react';
import {Task, CurrentUser} from 'modules/types';
import {useUnassignTask} from 'modules/mutations/useUnassignTask';
import {useAssignTask} from 'modules/mutations/useAssignTask';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {AssigneeTag} from 'Tasks/AssigneeTag';
import {CheckmarkFilled} from '@carbon/react/icons';
import styles from './styles.module.scss';

type AssignmentStatus =
  | 'off'
  | 'assigning'
  | 'unassigning'
  | 'assignmentSuccessful'
  | 'unassignmentSuccessful';

const ASSIGNMENT_TOGGLE_LABEL = {
  assigning: 'Assigning...',
  unassigning: 'Unassigning...',
  assignmentSuccessful: 'Assignment successful',
  unassignmentSuccessful: 'Unassignment successful',
} as const;

type Props = {
  children?: React.ReactNode;
  task: Task;
  onAssignmentError: () => void;
  user: CurrentUser;
};

const Details: React.FC<Props> = ({
  children,
  onAssignmentError,
  task,
  user,
}) => {
  const {
    id,
    name,
    processName,
    creationDate,
    completionDate,
    dueDate,
    followUpDate,
    assignee,
    taskState,
    candidateUsers,
    candidateGroups,
    tenantId,
  } = task;
  const taskTenant =
    user.tenants.length > 1
      ? user.tenants.find((tenant) => tenant.id === tenantId)
      : undefined;
  const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];
  const isAssigned = assignee !== null;
  const [assignmentStatus, setAssignmentStatus] =
    useState<AssignmentStatus>('off');
  const {mutateAsync: assignTask, isPending: assignIsPending} = useAssignTask();
  const {mutateAsync: unassignTask, isPending: unassignIsPending} =
    useUnassignTask();
  const isPending = (assignIsPending || unassignIsPending) ?? false;

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
    if (isPending || assignmentStatus !== 'off') {
      const ACTIVE_STATES: AssignmentStatus[] = ['assigning', 'unassigning'];

      return ACTIVE_STATES.includes(assignmentStatus) ? 'active' : 'finished';
    }

    return 'inactive';
  }

  return (
    <div className={styles.container} data-testid="details-info">
      <Section className={styles.content} level={4}>
        <TurnOnNotificationPermission />
        <TaskDetailsRow
          className={styles.header}
          as="header"
          title="Task details header"
        >
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
                      <span
                        className={styles.taskAssignee}
                        data-testid="assignee"
                      >
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
                  <AsyncActionButton
                    inlineLoadingProps={{
                      description:
                        assignmentStatus === 'off'
                          ? undefined
                          : ASSIGNMENT_TOGGLE_LABEL[assignmentStatus],
                      'aria-live': ['assigning', 'unassigning'].includes(
                        assignmentStatus,
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
                      disabled: isPending,
                      autoFocus: true,
                      id: 'main-content',
                    }}
                    status={getAsyncActionButtonStatus()}
                    key={id}
                  >
                    {isAssigned ? 'Unassign' : 'Assign to me'}
                  </AsyncActionButton>
                </span>
              </Restricted>
            )}
          </div>
        </TaskDetailsRow>
        {children}
      </Section>
      <aside className={styles.aside} aria-label="Task details right panel">
        <ContainedList label="Details" kind="disclosed">
          <>
            {taskTenant === undefined ? null : (
              <ContainedListItem>
                <span className={styles.itemHeading}>Tenant</span>
                <br />
                <span className={styles.itemBody}>{taskTenant.name}</span>
              </ContainedListItem>
            )}
          </>
          <ContainedListItem>
            <span className={styles.itemHeading}>Creation date</span>
            <br />
            <span className={styles.itemBody}>{formatDate(creationDate)}</span>
          </ContainedListItem>
          <ContainedListItem>
            <span className={styles.itemHeading}>Candidates</span>
            <br />
            {candidates.length === 0 ? (
              <span className={styles.itemBody}>No candidates</span>
            ) : null}
            {candidates.map((candidate) => (
              <Tag size="sm" type="gray" key={candidate}>
                {candidate}
              </Tag>
            ))}
          </ContainedListItem>
          {completionDate ? (
            <ContainedListItem>
              <span className={styles.itemHeading}>Completion date</span>
              <br />
              <span className={styles.itemBody}>
                {formatDate(completionDate)}
              </span>
            </ContainedListItem>
          ) : null}
          <ContainedListItem>
            <span className={styles.itemHeading}>Due date</span>
            <br />
            <span className={styles.itemBody}>
              {dueDate ? formatDate(dueDate) : 'No due date'}
            </span>
          </ContainedListItem>
          {followUpDate ? (
            <ContainedListItem>
              <span className={styles.itemHeading}>Follow up date</span>
              <br />
              <span className={styles.itemBody}>
                {formatDate(followUpDate)}
              </span>
            </ContainedListItem>
          ) : null}
        </ContainedList>
      </aside>
    </div>
  );
};

export {Details};
