/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';
import {useQuery} from '@apollo/react-hooks';
import {useParams} from 'react-router-dom';
import {Field, useForm} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';

import {
  GET_TASK_VARIABLES,
  GetTaskVariables,
  TaskVariablesQueryVariables,
} from 'modules/queries/get-task-variables';

import {Table, TD, ColumnTH, TR} from 'modules/components/Table/styled';
import {
  Title,
  EmptyMessage,
  EditTextarea,
  CreateButton,
  Plus,
  Header,
  Cross,
  NameInputTD,
  ValueInputTD,
  RemoveButtonTD,
  NameInput,
  IconButton,
  RowTH,
} from './styled';

const Variables: React.FC<{canEdit?: boolean}> = ({canEdit}) => {
  const {id: taskId} = useParams();
  const {data, loading} = useQuery<
    GetTaskVariables,
    TaskVariablesQueryVariables
  >(GET_TASK_VARIABLES, {
    variables: {id: taskId},
  });
  const form = useForm();

  useEffect(() => {
    if (!canEdit && !form.getState().submitting) {
      form.reset();
    }
  }, [canEdit, form]);

  if (loading || data === undefined) {
    return null;
  }
  const {variables} = data.task;

  return (
    <div>
      <Header>
        <Title>Variables</Title>
        {canEdit && (
          <CreateButton
            type="button"
            onClick={() => {
              form.mutators.push('new-variables');
            }}
          >
            <Plus /> Add Variable
          </CreateButton>
        )}
      </Header>

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
                      <ValueInputTD>
                        <Field
                          name={variable.name}
                          initialValue={variable.value}
                        >
                          {({input}) => (
                            <EditTextarea
                              {...input}
                              id={variable.name}
                              required
                            />
                          )}
                        </Field>
                      </ValueInputTD>
                      <RemoveButtonTD />
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
            {canEdit && (
              <FieldArray name="new-variables">
                {({fields}) =>
                  fields.map((variable, index) => (
                    <TR key={variable}>
                      <NameInputTD>
                        <Field name={`${variable}.name`}>
                          {({input}) => (
                            <NameInput
                              {...input}
                              placeholder="Variable"
                              aria-label={`${variable}.name`}
                              required
                            />
                          )}
                        </Field>
                      </NameInputTD>
                      <ValueInputTD>
                        <Field name={`${variable}.value`}>
                          {({input}) => (
                            <EditTextarea
                              {...input}
                              aria-label={`${variable}.value`}
                              placeholder="Value"
                              required
                            />
                          )}
                        </Field>
                      </ValueInputTD>
                      <RemoveButtonTD>
                        <IconButton
                          type="button"
                          aria-label={`Remove new variable ${index}`}
                          onClick={() => {
                            fields.remove(index);
                          }}
                        >
                          <Cross />
                        </IconButton>
                      </RemoveButtonTD>
                    </TR>
                  ))
                }
              </FieldArray>
            )}
          </tbody>
        </Table>
      )}
    </div>
  );
};
export {Variables};
