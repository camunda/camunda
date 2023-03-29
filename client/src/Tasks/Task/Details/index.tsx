/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMutation} from '@apollo/client';
import {GetTask} from 'modules/queries/get-task';
import {CLAIM_TASK, ClaimTaskVariables} from 'modules/mutations/claim-task';
import {
  UNCLAIM_TASK,
  UnclaimTaskVariables,
} from 'modules/mutations/unclaim-task';
import {formatDate} from 'modules/utils/formatDate';
import {TaskStates} from 'modules/constants/taskStates';
import {
  Aside,
  ClaimButtonContainer,
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
  | 'claiming'
  | 'unclaiming'
  | 'claimingSuccessful'
  | 'unclaimingSuccessful';

const ASSIGNMENT_TOGGLE_LABEL = {
  claiming: 'Claiming...',
  unclaiming: 'Unclaiming...',
  claimingSuccessful: 'Claiming successful',
  unclaimingSuccessful: 'Unclaiming successful',
} as const;

type Props = {
  children?: React.ReactNode;
  task: GetTask['task'];
  onAssigmentError: () => void;
};

const Details: React.FC<Props> = ({children, onAssigmentError, task}) => {
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
  const [claimTask, {loading: claimLoading}] = useMutation<
    GetTask,
    ClaimTaskVariables
  >(CLAIM_TASK, {
    variables: {id},
  });
  const [unclaimTask, {loading: unclaimLoading}] = useMutation<
    GetTask,
    UnclaimTaskVariables
  >(UNCLAIM_TASK, {
    variables: {id},
  });
  const isLoading = (claimLoading || unclaimLoading) ?? false;

  const handleClick = async () => {
    try {
      if (isAssigned) {
        setAssignmentStatus('unclaiming');
        await unclaimTask();
        setAssignmentStatus('unclaimingSuccessful');
        tracking.track({eventName: 'task-unclaimed'});
      } else {
        setAssignmentStatus('claiming');
        await claimTask();
        setAssignmentStatus('claimingSuccessful');
        tracking.track({eventName: 'task-claimed'});
      }
    } catch (error) {
      const errorMessage = (error as Error).message ?? '';

      setAssignmentStatus('off');

      if (shouldDisplayNotification(errorMessage)) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: isAssigned
            ? 'Task could not be unclaimed'
            : 'Task could not be claimed',
          subtitle: getTaskAssignmentChangeErrorMessage(errorMessage),
          isDismissable: true,
        });
      }

      // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getTaskAssigmentChangeErrorMessage
      if (shouldFetchMore(errorMessage)) {
        onAssigmentError();
      }
    }
  };

  function getAsyncActionButtonStatus() {
    if (isLoading || assignmentStatus !== 'off') {
      const ACTIVE_STATES: AssignmentStatus[] = ['claiming', 'unclaiming'];

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
                <ClaimButtonContainer>
                  <AsyncActionButton
                    inlineLoadingProps={{
                      description:
                        assignmentStatus === 'off'
                          ? undefined
                          : ASSIGNMENT_TOGGLE_LABEL[assignmentStatus],
                      'aria-live': ['claiming', 'unclaiming'].includes(
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
                    {isAssigned ? 'Unclaim' : 'Claim'}
                  </AsyncActionButton>
                </ClaimButtonContainer>
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
