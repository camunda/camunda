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
import {Table, RowTH, TD, TR} from 'modules/components/Table';
import {formatDate} from 'modules/utils/formatDate';
import {TaskStates} from 'modules/constants/taskStates';
import {
  Container,
  ClaimButton,
  Hint,
  Info,
  AssigneeTD,
  Assignee,
  Spinner,
} from './styled';
import {GetTasks, GET_TASKS} from 'modules/queries/get-tasks';
import {FilterValues} from 'modules/constants/filterValues';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {useNotifications} from 'modules/notifications';
import {shouldFetchMore} from './shouldFetchMore';
import {shouldDisplayNotification} from './shouldDisplayNotification';
import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';
import {Restricted} from 'modules/components/Restricted';
import {getAssigneeName} from 'modules/utils/getAssigneeName';

import {
  GET_CURRENT_USER,
  GetCurrentUser,
} from 'modules/queries/get-current-user';
import {
  MAX_TASKS_PER_REQUEST,
  MAX_TASKS_DISPLAYED,
} from 'modules/constants/tasks';
import {getSortValues} from '../getSortValues';
import {tracking} from 'modules/tracking';

const Details: React.FC = () => {
  const {id = ''} = useParams<{id: string}>();

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
  const notifications = useNotifications();

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
        await unclaimTask();
        tracking.track({eventName: 'task-unclaimed'});
      } else {
        await claimTask();
        tracking.track({eventName: 'task-claimed'});
      }
    } catch (error) {
      const errorMessage = (error as Error).message ?? '';

      if (shouldDisplayNotification(errorMessage)) {
        notifications.displayNotification('error', {
          headline: assignee
            ? 'Task could not be unclaimed'
            : 'Task could not be claimed',
          description: getTaskAssignmentChangeErrorMessage(errorMessage),
        });
      }

      // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getTaskAssigmentChangeErrorMessage
      if (shouldFetchMore(errorMessage)) {
        fetchMore({variables: {id}});
      }
    }
  };

  return (
    <Container>
      <Table data-testid="details-table">
        <tbody>
          <TR>
            <RowTH>Name</RowTH>
            <TD>{name}</TD>
          </TR>
          <TR>
            <RowTH>Process</RowTH>
            <TD>{processName}</TD>
          </TR>
          <TR>
            <RowTH>Creation Time</RowTH>
            <TD>{formatDate(creationTime)}</TD>
          </TR>
          {completionTime && (
            <TR>
              <RowTH>Completion Time</RowTH>
              <TD>{formatDate(completionTime)}</TD>
            </TR>
          )}

          <TR>
            <RowTH>Assignee</RowTH>
            <AssigneeTD>
              <Assignee data-testid="assignee-task-details">
                {getAssigneeName(assignee)}
                {taskState === TaskStates.Created && (
                  <Restricted scopes={['write']}>
                    <ClaimButton
                      variant="small"
                      type="button"
                      onClick={() => handleClick()}
                      disabled={isLoading}
                    >
                      {isLoading && <Spinner data-testid="spinner" />}
                      {assignee ? 'Unclaim' : 'Claim'}
                    </ClaimButton>
                  </Restricted>
                )}
              </Assignee>
              {!assignee && (
                <Restricted scopes={['write']}>
                  <Hint>
                    <Info />
                    Claim the Task to start working on it
                  </Hint>
                </Restricted>
              )}
            </AssigneeTD>
          </TR>
        </tbody>
      </Table>
    </Container>
  );
};

export {Details};
