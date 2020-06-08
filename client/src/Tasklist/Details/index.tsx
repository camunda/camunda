/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery} from '@apollo/react-hooks';
import {useParams} from 'react-router-dom';
import * as React from 'react';

import {GET_TASK, GetTask, GetTaskVariables} from 'modules/queries/get-task';
import {Table, RowTH, TD, TR} from 'modules/components/Table/styled';
import {formatDate} from 'modules/utils/formatDate';
import {Container} from './styled';

const Details: React.FC = () => {
  const {key} = useParams();
  const {data, loading} = useQuery<GetTask, GetTaskVariables>(GET_TASK, {
    variables: {key},
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
              {assignee ? `${assignee.firstname} ${assignee.lastname}` : '--'}
            </TD>
          </TR>
        </tbody>
      </Table>
    </Container>
  );
};

export {Details};
