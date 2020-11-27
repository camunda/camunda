/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery, useMutation} from '@apollo/client';
import {useParams} from 'react-router-dom';
import * as React from 'react';

import {
  GET_TASK_DETAILS,
  GetTaskDetails,
  TaskDetailsQueryVariables,
} from 'modules/queries/get-task-details';
import {CLAIM_TASK, ClaimTaskVariables} from 'modules/mutations/claim-task';
import {
  UNCLAIM_TASK,
  UnclaimTaskVariables,
} from 'modules/mutations/unclaim-task';
import {Table, RowTH, TD, TR} from 'modules/components/Table';
import {formatDate} from 'modules/utils/formatDate';
import {TaskStates} from 'modules/constants/taskStates';
import {Container, ClaimButton} from './styled';
import {GET_TASKS} from 'modules/queries/get-tasks';
import {FilterValues} from 'modules/constants/filterValues';
import {getSearchParam} from 'modules/utils/getSearchParam';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {useLocation} from 'react-router-dom';
import {
  GET_CURRENT_USER,
  GetCurrentUser,
} from 'modules/queries/get-current-user';
const Details: React.FC = () => {
  const {id} = useParams<{id: string}>();
  const location = useLocation();
  const filter =
    getSearchParam('filter', location.search) ?? FilterValues.AllOpen;
  const isClaimedByMeFilter = filter === FilterValues.ClaimedByMe;
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER, {
    skip: !isClaimedByMeFilter,
  });
  const [claimTask] = useMutation<GetTaskDetails, ClaimTaskVariables>(
    CLAIM_TASK,
    {
      variables: {id},
      refetchQueries: [
        {
          query: GET_TASKS,
          variables: getQueryVariables(filter, {
            username: userData?.currentUser.username,
          }),
        },
      ],
    },
  );

  const [unclaimTask] = useMutation<GetTaskDetails, UnclaimTaskVariables>(
    UNCLAIM_TASK,
    {
      variables: {id},
      refetchQueries: [
        {
          query: GET_TASKS,
          variables: getQueryVariables(filter, {
            username: userData?.currentUser.username,
          }),
        },
      ],
    },
  );

  const {data, loading} = useQuery<GetTaskDetails, TaskDetailsQueryVariables>(
    GET_TASK_DETAILS,
    {
      variables: {id},
    },
  );

  if (loading || data === undefined) {
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
            <TD data-testid="assignee">
              {assignee ? (
                <>
                  {assignee.firstname} {assignee.lastname}
                  {taskState === TaskStates.Created && (
                    <ClaimButton
                      variant="small"
                      onClick={() => {
                        unclaimTask().catch(() => {
                          // TODO: handle 'Task could not be unclaimed' errors https://github.com/zeebe-io/zeebe-tasklist/issues/507
                        });
                      }}
                    >
                      Unclaim
                    </ClaimButton>
                  )}
                </>
              ) : (
                <>
                  --
                  {taskState === TaskStates.Created && (
                    <ClaimButton
                      variant="small"
                      onClick={() => {
                        claimTask().catch(() => {
                          // TODO: handle 'Task could not be claimed' errors https://github.com/zeebe-io/zeebe-tasklist/issues/507
                        });
                      }}
                    >
                      Claim
                    </ClaimButton>
                  )}
                </>
              )}
            </TD>
          </TR>
        </tbody>
      </Table>
    </Container>
  );
};

export {Details};
