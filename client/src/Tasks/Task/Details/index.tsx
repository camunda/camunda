/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMutation} from '@apollo/client';
import {GetTask} from 'modules/queries/get-task';
import {ASSIGN_TASK, AssignTaskVariables} from 'modules/mutations/assign-task';
import {
  UNASSIGN_TASK,
  UnassignTaskVariables,
} from 'modules/mutations/unassign-task';
import {formatDate} from 'modules/utils/formatDate';
import {TaskStates} from 'modules/constants/taskStates';
import {
  Aside,
  AssignButtonContainer,
  Container,
  Content,
  Header,
  HeaderLeftContainer,
  HeaderRightContainer,
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
  task: GetTask['task'];
  onAssignmentError: () => void;
};

const Details: React.FC<Props> = ({children, onAssignmentError, task}) => {
  const {
    id,
    name,
    processName,
    creationTime,
    completionTime,
    dueDate,
    followUpDate,
    assignee,
    taskState,
    candidateUsers,
    candidateGroups,
  } = task;
  const candidates = [...(candidateUsers ?? []), ...(candidateGroups ?? [])];
  const isAssigned = assignee !== null;
  const [assignmentStatus, setAssignmentStatus] =
    useState<AssignmentStatus>('off');
  const [assignTask, {loading: assignLoading}] = useMutation<
    GetTask,
    AssignTaskVariables
  >(ASSIGN_TASK, {
    variables: {id},
  });
  const [unassignTask, {loading: unassignLoading}] = useMutation<
    GetTask,
    UnassignTaskVariables
  >(UNASSIGN_TASK, {
    variables: {id},
  });
  const isLoading = (assignLoading || unassignLoading) ?? false;

  const handleClick = async () => {
    try {
      if (isAssigned) {
        setAssignmentStatus('unassigning');
        await unassignTask();
        setAssignmentStatus('unassignmentSuccessful');
        tracking.track({eventName: 'task-unassigned'});
      } else {
        setAssignmentStatus('assigning');
        await assignTask();
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
        <Header as="header" title="Task details header">
          <HeaderLeftContainer>
            <BodyCompact $variant="02">{name}</BodyCompact>
            <Label $color="secondary">{processName}</Label>
          </HeaderLeftContainer>
          <HeaderRightContainer>
            <Label $color="secondary">
              {isAssigned ? 'Assigned' : 'Unassigned'}
            </Label>
            {taskState === TaskStates.Created && (
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
      <Aside>
        <ContainedList label="Details" kind="disclosed">
          <ContainedListItem>
            <BodyCompact $color="secondary">Creation date</BodyCompact>
            <br />
            <BodyCompact>{formatDate(creationTime)}</BodyCompact>
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
          <ContainedListItem>
            <BodyCompact $color="secondary">Completion date</BodyCompact>
            <br />
            <BodyCompact>
              {completionTime ? formatDate(completionTime) : 'Pending task'}
            </BodyCompact>
          </ContainedListItem>
          <ContainedListItem>
            <BodyCompact $color="secondary">Due date</BodyCompact>
            <br />
            <BodyCompact>
              {dueDate ? formatDate(dueDate) : 'No due date'}
            </BodyCompact>
          </ContainedListItem>
          <ContainedListItem>
            <BodyCompact $color="secondary">Follow up date</BodyCompact>
            <br />
            <BodyCompact>
              {followUpDate ? formatDate(followUpDate) : 'No follow up date'}
            </BodyCompact>
          </ContainedListItem>
        </ContainedList>
      </Aside>
    </Container>
  );
};

export {Details};
