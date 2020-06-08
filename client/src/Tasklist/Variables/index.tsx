/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useQuery} from '@apollo/react-hooks';
import {useParams} from 'react-router-dom';

import {GET_TASK_VARIABLES, GetVariables} from 'modules/queries/get-variables';
import {GetTaskVariables} from 'modules/queries/get-task';
import {Table, TD, RowTH, ColumnTH, TR} from 'modules/components/Table/styled';
import {Title, EmptyMessage} from './styled';

const Variables: React.FC = () => {
  const {key} = useParams();
  const {data, loading} = useQuery<GetVariables, GetTaskVariables>(
    GET_TASK_VARIABLES,
    {
      variables: {key},
    },
  );

  if (loading || data === undefined) {
    return null;
  }

  const {variables} = data.task;

  return (
    <>
      <Title>Variables</Title>

      {variables.length <= 0 ? (
        <EmptyMessage>Task has no variables.</EmptyMessage>
      ) : (
        <Table data-testid="variables-table">
          <thead>
            <TR hasNoBorder>
              <ColumnTH>Variable</ColumnTH>
              <ColumnTH>Value</ColumnTH>
            </TR>
          </thead>
          <tbody>
            {variables.map((variable) => (
              <TR key={variable.name}>
                <RowTH>{variable.name}</RowTH>
                <TD>{variable.value}</TD>
              </TR>
            ))}
          </tbody>
        </Table>
      )}
    </>
  );
};
export {Variables};
