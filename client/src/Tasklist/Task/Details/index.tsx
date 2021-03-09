/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery, useMutation} from '@apollo/client';
import {useParams} from 'react-router-dom';
import * as React from 'react';

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
} from './styled';
import {GetTasks, GET_TASKS} from 'modules/queries/get-tasks';
import {FilterValues} from 'modules/constants/filterValues';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {getUserDisplayName} from 'modules/utils/getUserDisplayName';
import {useLocation} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';
import {shouldFetchMore} from './shouldFetchMore';
import {shouldDisplayNotification} from './shouldDisplayNotification';
import {getTaskAssignmentChangeErrorMessage} from './getTaskAssignmentChangeErrorMessage';

import {
  GET_CURRENT_USER,
  GetCurrentUser,
} from 'modules/queries/get-current-user';
import {
  MAX_TASKS_PER_REQUEST,
  MAX_TASKS_DISPLAYED,
} from 'modules/constants/tasks';
import {getSortValues} from '../getSortValues';

const Details: React.FC = () => {
  const {id} = useParams<{id: string}>();
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

  const [claimTask] = useMutation<GetTask, ClaimTaskVariables>(CLAIM_TASK, {
    variables: {id},
    refetchQueries: [
      {
        query: GET_TASKS,
        variables: {
          ...getQueryVariables(filter, {
            username: userData?.currentUser.username,
            pageSize:
              currentTaskCount <= MAX_TASKS_PER_REQUEST
                ? MAX_TASKS_PER_REQUEST
                : MAX_TASKS_DISPLAYED,
            searchAfterOrEqual: getSortValues(dataFromCache?.tasks),
          }),
        },
      },
    ],
  });

  const [unclaimTask] = useMutation<GetTask, UnclaimTaskVariables>(
    UNCLAIM_TASK,
    {
      variables: {id},
      refetchQueries: [
        {
          query: GET_TASKS,
          variables: {
            ...getQueryVariables(filter, {
              username: userData?.currentUser.username,
              pageSize:
                currentTaskCount <= MAX_TASKS_PER_REQUEST
                  ? MAX_TASKS_PER_REQUEST
                  : MAX_TASKS_DISPLAYED,
              searchAfterOrEqual: getSortValues(dataFromCache?.tasks),
            }),
          },
        },
      ],
    },
  );

  const {data, fetchMore} = useTask(id);

  const notifications = useNotifications();

  if (data === undefined) {
    return null;
  }

  const {
    task: {
      name,
      workflowName,
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
      } else {
        await claimTask();
      }
    } catch (error) {
      if (shouldDisplayNotification(error.message)) {
        notifications.displayNotification('error', {
          headline: assignee
            ? 'Task could not be unclaimed'
            : 'Task could not be claimed',
          description: getTaskAssignmentChangeErrorMessage(error.message),
        });
      }

      // TODO: this does not have to be a separate function, once we are able to use error codes we can move this inside getTaskAssigmentChangeErrorMessage
      if (shouldFetchMore(error.message)) {
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
            <RowTH>Workflow</RowTH>
            <TD>{workflowName}</TD>
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
                {getUserDisplayName(assignee)}
                {taskState === TaskStates.Created && (
                  <ClaimButton
                    variant="small"
                    type="button"
                    onClick={() => handleClick()}
                  >
                    {assignee ? 'Unclaim' : 'Claim'}
                  </ClaimButton>
                )}
              </Assignee>
              {!assignee && (
                <Hint>
                  <Info />
                  Claim the Task to start working on it
                </Hint>
              )}
            </AssigneeTD>
          </TR>
        </tbody>
      </Table>
    </Container>
  );
};

export {Details};
