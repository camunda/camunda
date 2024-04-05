/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatDate} from 'modules/utils/formatDate';
import {
  Aside,
  AssignButtonContainer,
  Container,
  Content,
  Header,
  HeaderLeftContainer,
  HeaderRightContainer,
  StackCenterAligned,
} from './styled';
import {shouldFetchMore} from './shouldFetchMore';
import {shouldDisplayNotification} from './shouldDisplayNotification';
import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';
import {Restricted} from 'modules/components/Restricted';
import {tracking} from 'modules/tracking';
import {useState} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {BodyCompact, Label} from 'modules/components/FontTokens';
import {ContainedList, ContainedListItem, Tag} from '@carbon/react';
import {Task, CurrentUser} from 'modules/types';
import {useUnassignTask} from 'modules/mutations/useUnassignTask';
import {useAssignTask} from 'modules/mutations/useAssignTask';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {AssigneeTag} from 'Tasks/AssigneeTag';
import {CheckmarkFilled} from '@carbon/icons-react';

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
  const {mutateAsync: assignTask, isLoading: assignIsLoading} = useAssignTask();
  const {mutateAsync: unassignTask, isLoading: unassignIsLoading} =
    useUnassignTask();
  const isLoading = (assignIsLoading || unassignIsLoading) ?? false;

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
    <Container data-testid="details-info">
      <Content level={4}>
        <TurnOnNotificationPermission />
        <Header as="header" title="Task details header">
          <HeaderLeftContainer>
            <BodyCompact $variant="02">{name}</BodyCompact>
            <Label $color="secondary">{processName}</Label>
          </HeaderLeftContainer>
          <HeaderRightContainer>
            {taskState === 'COMPLETED' ? (
              <Label
                $color="secondary"
                data-testid="completion-label"
                title="Completed by"
              >
                <StackCenterAligned orientation="horizontal" gap={2}>
                  <CheckmarkFilled size={16} color="green" />
                  Completed
                  {assignee ? (
                    <>
                      {' '}
                      by
                      <Label $color="secondary" data-testid="assignee">
                        <AssigneeTag
                          currentUser={user}
                          assignee={assignee}
                          isShortFormat={true}
                        />
                      </Label>
                    </>
                  ) : null}
                </StackCenterAligned>
              </Label>
            ) : (
              <Label $color="secondary" data-testid="assignee">
                <AssigneeTag
                  currentUser={user}
                  assignee={assignee}
                  isShortFormat={false}
                />
              </Label>
            )}
            {taskState === 'CREATED' && (
              <Restricted scopes={['write']}>
                <AssignButtonContainer>
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
                      disabled: isLoading,
                      autoFocus: true,
                      id: 'main-content',
                    }}
                    status={getAsyncActionButtonStatus()}
                    key={id}
                  >
                    {isAssigned ? 'Unassign' : 'Assign to me'}
                  </AsyncActionButton>
                </AssignButtonContainer>
              </Restricted>
            )}
          </HeaderRightContainer>
        </Header>
        {children}
      </Content>
      <Aside aria-label="Task details right panel">
        <ContainedList label="Details" kind="disclosed">
          <>
            {taskTenant === undefined ? null : (
              <ContainedListItem>
                <BodyCompact $color="secondary">Tenant</BodyCompact>
                <br />
                <BodyCompact>{taskTenant.name}</BodyCompact>
              </ContainedListItem>
            )}
          </>
          <ContainedListItem>
            <BodyCompact $color="secondary">Creation date</BodyCompact>
            <br />
            <BodyCompact>{formatDate(creationDate)}</BodyCompact>
          </ContainedListItem>
          <ContainedListItem>
            <BodyCompact $color="secondary">Candidates</BodyCompact>
            <br />
            {candidates.length === 0 ? (
              <BodyCompact>No candidates</BodyCompact>
            ) : null}
            {candidates.map((candidate) => (
              <Tag size="sm" type="gray" key={candidate}>
                {candidate}
              </Tag>
            ))}
          </ContainedListItem>
          {completionDate ? (
            <ContainedListItem>
              <BodyCompact $color="secondary">Completion date</BodyCompact>
              <br />
              <BodyCompact>{formatDate(completionDate)}</BodyCompact>
            </ContainedListItem>
          ) : null}
          <ContainedListItem>
            <BodyCompact $color="secondary">Due date</BodyCompact>
            <br />
            <BodyCompact>
              {dueDate ? formatDate(dueDate) : 'No due date'}
            </BodyCompact>
          </ContainedListItem>
          {followUpDate ? (
            <ContainedListItem>
              <BodyCompact $color="secondary">Follow up date</BodyCompact>
              <br />
              <BodyCompact>{formatDate(followUpDate)}</BodyCompact>
            </ContainedListItem>
          ) : null}
        </ContainedList>
      </Aside>
    </Container>
  );
};

export {Details};
