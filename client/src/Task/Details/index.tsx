/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery, useMutation} from '@apollo/client';
import {useParams, useLocation} from 'react-router-dom';
import {GetTask, useTask} from 'modules/queries/get-task';
import {CLAIM_TASK, ClaimTaskVariables} from 'modules/mutations/claim-task';
import {
  UNCLAIM_TASK,
  UnclaimTaskVariables,
} from 'modules/mutations/unclaim-task';
import {Table, LeftTD, RightTD, TR} from 'modules/components/Table';
import {formatDate} from 'modules/utils/formatDate';
import {TaskStates} from 'modules/constants/taskStates';
import {
  Assignee,
  ClaimButtonContainer,
  HelperText,
  AssigneeText,
} from './styled';
import {GetTasks, GET_TASKS} from 'modules/queries/get-tasks';
import {FilterValues} from 'modules/constants/filterValues';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {shouldFetchMore} from './shouldFetchMore';
import {shouldDisplayNotification} from './shouldDisplayNotification';
import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';
import {Restricted} from 'modules/components/Restricted';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
} from 'modules/queries/get-current-user';
import {
  MAX_TASKS_PER_REQUEST,
  MAX_TASKS_DISPLAYED,
} from 'modules/constants/tasks';
import {getSortValues} from 'modules/utils/getSortValues';
import {tracking} from 'modules/tracking';
import {useState} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';

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

const Details: React.FC = () => {
  const {id = ''} = useParams<{id: string}>();
  const [assignmentStatus, setAssignmentStatus] =
    useState<AssignmentStatus>('off');
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;
  const isClaimedByMeFilter = filter === FilterValues.ClaimedByMe;
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER, {
    skip: !isClaimedByMeFilter,
  });

  const {data: dataFromCache} = useQuery<GetTasks>(GET_TASKS, {
    fetchPolicy: 'cache-only',
  });
  const currentTaskCount = dataFromCache?.tasks?.length ?? 0;
  const [claimTask, {loading: claimLoading}] = useMutation<
    GetTask,
    ClaimTaskVariables
  >(CLAIM_TASK, {
    variables: {id},
    refetchQueries: [
      {
        query: GET_TASKS,
        variables: {
          ...getQueryVariables(filter, {
            userId: userData?.currentUser.userId,
            pageSize:
              currentTaskCount <= MAX_TASKS_PER_REQUEST
                ? MAX_TASKS_PER_REQUEST
                : MAX_TASKS_DISPLAYED,
            searchAfterOrEqual: getSortValues(dataFromCache?.tasks),
          }),
          isRunAfterMutation: true,
        },
      },
    ],
  });

  const [unclaimTask, {loading: unclaimLoading}] = useMutation<
    GetTask,
    UnclaimTaskVariables
  >(UNCLAIM_TASK, {
    variables: {id},
    refetchQueries: [
      {
        query: GET_TASKS,
        variables: {
          ...getQueryVariables(filter, {
            userId: userData?.currentUser.userId,
            pageSize:
              currentTaskCount <= MAX_TASKS_PER_REQUEST
                ? MAX_TASKS_PER_REQUEST
                : MAX_TASKS_DISPLAYED,
            searchAfterOrEqual: getSortValues(dataFromCache?.tasks),
          }),
          isRunAfterMutation: true,
        },
      },
    ],
  });

  const {data, fetchMore} = useTask(id);
  const isLoading = (claimLoading || unclaimLoading) ?? false;

  if (data === undefined) {
    return null;
  }

  const {
    task: {
      name,
      processName,
      creationTime,
      completionTime,
      assignee,
      taskState,
    },
  } = data;

  const handleClick = async () => {
    try {
      if (assignee !== null) {
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
          title: assignee
            ? 'Task could not be unclaimed'
            : 'Task could not be claimed',
          subtitle: getTaskAssignmentChangeErrorMessage(errorMessage),
          isDismissable: true,
        });
      }

      // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getTaskAssigmentChangeErrorMessage
      if (shouldFetchMore(errorMessage)) {
        fetchMore({variables: {id}});
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
    <div>
      <Table data-testid="details-table">
        <tbody>
          <TR>
            <LeftTD>Task Name</LeftTD>
            <RightTD>{name}</RightTD>
          </TR>
          <TR>
            <LeftTD>Process Name</LeftTD>
            <RightTD>{processName}</RightTD>
          </TR>
          <TR>
            <LeftTD>Creation Date</LeftTD>
            <RightTD>{formatDate(creationTime)}</RightTD>
          </TR>
          {completionTime && (
            <TR>
              <LeftTD>Completion Date</LeftTD>
              <RightTD>{formatDate(completionTime)}</RightTD>
            </TR>
          )}

          <TR>
            <LeftTD>Assignee</LeftTD>
            <RightTD>
              <Assignee data-testid="assignee-task-details">
                <AssigneeText>
                  {assignee ? (
                    assignee
                  ) : (
                    <Restricted scopes={['write']}>
                      <>
                        Unassigned
                        <HelperText>
                          &nbsp;- claim task to work on this task.
                        </HelperText>
                      </>
                    </Restricted>
                  )}
                </AssigneeText>
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
                          kind: assignee ? 'ghost' : 'primary',
                          size: 'sm',
                          type: 'button',
                          onClick: handleClick,
                          disabled: isLoading,
                          autoFocus: true,
                        }}
                        status={getAsyncActionButtonStatus()}
                        key={id}
                      >
                        {assignee ? 'Unclaim' : 'Claim'}
                      </AsyncActionButton>
                    </ClaimButtonContainer>
                  </Restricted>
                )}
              </Assignee>
            </RightTD>
          </TR>
        </tbody>
      </Table>
    </div>
  );
};

export {Details};
