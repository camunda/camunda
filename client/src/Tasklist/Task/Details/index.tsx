/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery, useMutation} from '@apollo/react-hooks';
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
import {Table, RowTH, TD, TR} from 'modules/components/Table/styled';
import {formatDate} from 'modules/utils/formatDate';
import {TaskStates} from 'modules/constants/taskStates';
import {Container, ClaimButton} from './styled';

const Details: React.FC = () => {
  const {id} = useParams();

  const [claimTask] = useMutation<GetTaskDetails, ClaimTaskVariables>(
    CLAIM_TASK,
    {
      variables: {id},
      refetchQueries: [{query: GET_TASK_DETAILS, variables: {id}}],
    },
  );

  const [unclaimTask] = useMutation<GetTaskDetails, UnclaimTaskVariables>(
    UNCLAIM_TASK,
    {
      variables: {id},
      refetchQueries: [{query: GET_TASK_DETAILS, variables: {id}}],
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
                    <ClaimButton onClick={() => unclaimTask()}>
                      Unclaim
                    </ClaimButton>
                  )}
                </>
              ) : (
                <>
                  --
                  <ClaimButton onClick={() => claimTask()}>Claim</ClaimButton>
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
