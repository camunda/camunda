/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useQuery} from '@apollo/react-hooks';
import {useParams} from 'react-router-dom';
import {Field} from 'react-final-form';

import {
  GET_TASK_VARIABLES,
  GetTaskVariables,
  TaskVariablesQueryVariables,
} from 'modules/queries/get-task-variables';

import {Table, TD, RowTH, ColumnTH, TR} from 'modules/components/Table/styled';
import {Title, EmptyMessage, EditTextarea} from './styled';

const Variables: React.FC<{canEdit?: boolean}> = ({canEdit}) => {
  const {id: taskId} = useParams();
  const {data, loading} = useQuery<
    GetTaskVariables,
    TaskVariablesQueryVariables
  >(GET_TASK_VARIABLES, {
    variables: {id: taskId},
  });

  if (loading || data === undefined) {
    return null;
  }
  const {variables} = data.task;

  return (
    <div>
      <Title>Variables</Title>
      {variables.length === 0 ? (
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
            {variables.map((variable) => {
              return (
                <TR key={variable.name}>
                  {canEdit ? (
                    <>
                      <RowTH>
                        <label htmlFor={variable.name}>{variable.name}</label>
                      </RowTH>
                      <td>
                        <Field
                          name={variable.name}
                          initialValue={variable.value}
                        >
                          {({input}) => (
                            <EditTextarea {...input} id={variable.name} />
                          )}
                        </Field>
                      </td>
                    </>
                  ) : (
                    <>
                      <RowTH>{variable.name}</RowTH>
                      <TD>{variable.value}</TD>
                    </>
                  )}
                </TR>
              );
            })}
          </tbody>
        </Table>
      )}
    </div>
  );
};
export {Variables};
