/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useQuery} from '@apollo/client';
import React, {useEffect, useRef} from 'react';
import {useParams} from 'react-router-dom';
import {Field, useForm, useField} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';

import {
  GET_TASK_VARIABLES,
  GetTaskVariables,
  TaskVariablesQueryVariables,
} from 'modules/queries/get-task-variables';
import {Table, TD, TR} from 'modules/components/Table';
import {
  Container,
  Body,
  Title,
  TableContainer,
  EmptyMessage,
  EditTextarea,
  CreateButton,
  Plus,
  Header,
  Cross,
  NameInputTD,
  ValueInputTD,
  VariableNameTH,
  VariableValueTH,
  IconTD,
  Warning,
  IconContainer,
  NameInput,
  IconButton,
  RowTH,
} from './styled';
import {validateJSON, validateNonEmpty} from './validators';

const Variables: React.FC<{canEdit?: boolean}> = ({canEdit}) => {
  const tableContainer = useRef<HTMLDivElement>(null);
  const {id: taskId} = useParams<{id: string}>();
  const {data, loading} = useQuery<
    GetTaskVariables,
    TaskVariablesQueryVariables
  >(GET_TASK_VARIABLES, {
    variables: {id: taskId},
  });
  const form = useForm();
  const newVariablesFieldArray = useField('new-variables');
  const newVariableCount = useRef(newVariablesFieldArray.input.value.length);

  useEffect(() => {
    const updatedLength = newVariablesFieldArray.input.value.length;
    if (
      tableContainer.current !== null &&
      updatedLength > newVariableCount.current
    ) {
      tableContainer.current.scrollTop = tableContainer.current.scrollHeight;
    }

    newVariableCount.current = updatedLength;
  }, [newVariablesFieldArray.input.value.length]);

  useEffect(() => {
    if (!canEdit && !form.getState().submitting) {
      form.reset();
    }
  }, [canEdit, form]);

  if (loading || data === undefined) {
    return null;
  }

  const handleAddVariable = () => {
    form.mutators.push('new-variables');
  };

  const isVariableDirty = (variable: string): boolean => {
    return Boolean(
      form.getFieldState(`${variable}.name`)?.dirty ||
        form.getFieldState(`${variable}.value`)?.dirty,
    );
  };

  const getError = (variable: string): string | void => {
    const nameFieldError = form.getFieldState(`${variable}.name`)?.error;
    const valueFieldError = form.getFieldState(`${variable}.value`)?.error;
    const isDirty = isVariableDirty(variable);

    if (
      !isDirty ||
      (nameFieldError === undefined && valueFieldError === undefined)
    ) {
      return;
    }

    if (nameFieldError !== undefined && valueFieldError !== undefined) {
      return `${nameFieldError} and ${valueFieldError}`;
    }

    return nameFieldError ?? valueFieldError;
  };

  const {variables} = data.task;

  return (
    <Container>
      <Header>
        <Title>Variables</Title>
        {canEdit && (
          <CreateButton
            type="button"
            variant="small"
            onClick={handleAddVariable}
          >
            <Plus /> Add Variable
          </CreateButton>
        )}
      </Header>
      <Body>
        {variables.length === 0 &&
        newVariablesFieldArray.input.value.length === 0 ? (
          <EmptyMessage>Task has no variables.</EmptyMessage>
        ) : (
          <TableContainer ref={tableContainer}>
            <Table data-testid="variables-table">
              <thead>
                <TR hasNoBorder>
                  <VariableNameTH>Variable</VariableNameTH>
                  <VariableValueTH colSpan={2}>Value</VariableValueTH>
                </TR>
              </thead>
              <tbody>
                {variables.map((variable) => {
                  return (
                    <TR key={variable.name}>
                      {canEdit ? (
                        <>
                          <RowTH>
                            <label htmlFor={variable.name}>
                              {variable.name}
                            </label>
                          </RowTH>
                          <ValueInputTD>
                            <Field
                              name={variable.name}
                              initialValue={variable.value}
                              validate={validateJSON}
                            >
                              {({input, meta}) => (
                                <EditTextarea
                                  {...input}
                                  id={variable.name}
                                  aria-invalid={meta.error !== undefined}
                                />
                              )}
                            </Field>
                          </ValueInputTD>
                          <IconTD>
                            {form.getFieldState(variable.name)?.error !==
                              undefined && (
                              <Warning
                                title={form.getFieldState(variable.name)?.error}
                                data-testid={`warning-icon-${variable.name}`}
                              />
                            )}
                          </IconTD>
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
                      fields.map((variable, index) => {
                        const error = getError(variable);
                        return (
                          <TR key={variable}>
                            <NameInputTD>
                              <Field
                                name={`${variable}.name`}
                                validate={validateNonEmpty}
                              >
                                {({input, meta}) => (
                                  <NameInput
                                    {...input}
                                    placeholder="Variable"
                                    aria-label={`${variable}.name`}
                                    aria-invalid={
                                      meta.error !== undefined &&
                                      isVariableDirty(variable)
                                    }
                                  />
                                )}
                              </Field>
                            </NameInputTD>
                            <ValueInputTD>
                              <Field
                                name={`${variable}.value`}
                                validate={validateJSON}
                              >
                                {({input, meta}) => (
                                  <EditTextarea
                                    {...input}
                                    aria-label={`${variable}.value`}
                                    placeholder="Value"
                                    aria-invalid={
                                      meta.error !== undefined &&
                                      isVariableDirty(variable)
                                    }
                                  />
                                )}
                              </Field>
                            </ValueInputTD>
                            <IconTD>
                              <IconContainer>
                                {error !== undefined && (
                                  <Warning
                                    title={error}
                                    data-testid={`warning-icon-${variable}.value`}
                                  />
                                )}
                                <IconButton
                                  type="button"
                                  aria-label={`Remove new variable ${index}`}
                                  onClick={() => {
                                    fields.remove(index);
                                  }}
                                >
                                  <Cross />
                                </IconButton>
                              </IconContainer>
                            </IconTD>
                          </TR>
                        );
                      })
                    }
                  </FieldArray>
                )}
              </tbody>
            </Table>
          </TableContainer>
        )}
      </Body>
    </Container>
  );
};
export {Variables};
