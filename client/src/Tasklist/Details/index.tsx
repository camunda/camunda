/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery, useMutation} from '@apollo/react-hooks';
import {useParams} from 'react-router-dom';
import * as React from 'react';

import {GET_TASK, GetTask, GetTaskVariables} from 'modules/queries/get-task';
import {CLAIM_TASK} from 'modules/mutations/claim-task';
import {UNCLAIM_TASK} from 'modules/mutations/unclaim-task';
import {Table, RowTH, TD, TR} from 'modules/components/Table/styled';
import {formatDate} from 'modules/utils/formatDate';
import {Container, ClaimButton} from './styled';

const Details: React.FC = () => {
  const {id} = useParams();

  const [claimTask] = useMutation<GetTask, GetTaskVariables>(CLAIM_TASK, {
    variables: {id},
    refetchQueries: [{query: GET_TASK, variables: {id}}],
  });

  const [unclaimTask] = useMutation<GetTask, GetTaskVariables>(UNCLAIM_TASK, {
    variables: {id},
    refetchQueries: [{query: GET_TASK, variables: {id}}],
  });

  const {data, loading} = useQuery<GetTask, GetTaskVariables>(GET_TASK, {
    variables: {id},
  });

  if (loading || data === undefined) {
    return null;
  }

  const {
    task: {name, workflowName, creationTime, completionTime, assignee},
  } = data;

  return (
    <Container>
      <Table>
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
                  <ClaimButton onClick={() => unclaimTask()}>
                    Unclaim
                  </ClaimButton>
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
