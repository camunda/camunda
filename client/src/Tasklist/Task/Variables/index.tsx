/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef} from 'react';
import {FieldState} from 'final-form';
import {Field, Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {get, intersection} from 'lodash';
import arrayMutators from 'final-form-arrays';
import {Button} from 'modules/components/Button';
import {Table, TD, TR} from 'modules/components/Table';
import {
  Container,
  Body,
  TableContainer,
  EmptyMessage,
  EditTextarea,
  CreateButton,
  Plus,
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
  Form as StyledForm,
} from './styled';
import {
  validateJSON,
  validateNonEmpty,
  validateDuplicateVariableName,
} from './validators';
import {createVariableFieldName} from './createVariableFieldName';
import {getVariableFieldName} from './getVariableFieldName';
import {Variable} from 'modules/types';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {useQuery} from '@apollo/client';
import {DetailsFooter} from 'modules/components/DetailsFooter';
import {ResetForm} from './ResetForm';
import {GetTask} from 'modules/queries/get-task';
import {FormValues} from './types';
import {PanelTitle} from 'modules/components/PanelTitle';
import {PanelHeader} from 'modules/components/PanelHeader';
import {useTaskVariables} from 'modules/queries/get-task-variables';

type Props = {
  onSubmit: (variables: Variable[]) => Promise<void>;
  task: GetTask['task'];
};

const Variables: React.FC<Props> = ({onSubmit, task}) => {
  const tableContainer = useRef<HTMLDivElement>(null);
  const {data: userData, loading} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const {variables, loading: areVariablesLoading} = useTaskVariables(task.id);

  if (loading || areVariablesLoading) {
    return null;
  }

  const {assignee, taskState} = task;
  const canCompleteTask =
    userData?.currentUser.username === assignee?.username &&
    taskState === 'CREATED';

  const isVariableDirty = (
    name: undefined | FieldState<string>,
    value: undefined | FieldState<string>,
  ): boolean => {
    return Boolean(name?.dirty || value?.dirty);
  };

  const getError = (
    name: undefined | FieldState<string>,
    value: undefined | FieldState<string>,
  ): string | void => {
    const nameFieldError = name?.error;
    const valueFieldError = value?.error;
    const isDirty = isVariableDirty(name, value);

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

  return (
    <Form<FormValues>
      mutators={{...arrayMutators}}
      onSubmit={async (values, form) => {
        const {dirtyFields, initialValues = []} = form.getState();

        const existingVariables: ReadonlyArray<Variable> = intersection(
          Object.keys(initialValues),
          Object.keys(dirtyFields),
        ).map((name) => ({
          name,
          value: values[name],
        }));

        const newVariables: ReadonlyArray<Variable> =
          get(values, 'newVariables') || [];

        await onSubmit([
          ...existingVariables.map((variable) => ({
            ...variable,
            name: getVariableFieldName(variable.name),
          })),
          ...newVariables,
        ]);
      }}
      validate={(values) => {
        const {newVariables} = values;

        if (newVariables !== undefined) {
          return {
            newVariables: newVariables.map((variable, index) => {
              if (variable === undefined) {
                return {
                  name: validateNonEmpty(''),
                  value: validateJSON(''),
                };
              }
              const {value, name} = variable;

              return {
                name:
                  validateNonEmpty(name) ??
                  validateDuplicateVariableName(variable, values, index),
                value: validateJSON(value),
              };
            }),
          };
        }

        return {};
      }}
    >
      {({form, handleSubmit, values}) => (
        <StyledForm onSubmit={handleSubmit} hasFooter={canCompleteTask}>
          <ResetForm isAssigned={canCompleteTask} />
          <Container>
            <PanelHeader>
              <PanelTitle>Variables</PanelTitle>
              {canCompleteTask && (
                <CreateButton
                  type="button"
                  variant="small"
                  onClick={() => {
                    const element = tableContainer.current;
                    if (element !== null) {
                      element.scrollTop = element.scrollHeight;
                    }

                    form.mutators.push('newVariables');
                  }}
                >
                  <Plus /> Add Variable
                </CreateButton>
              )}
            </PanelHeader>
            <Body>
              {variables.length >= 1 ||
              (values?.newVariables?.length !== undefined &&
                values?.newVariables?.length >= 1) ? (
                <TableContainer ref={tableContainer}>
                  <Table data-testid="variables-table">
                    <thead>
                      <TR hasNoBorder>
                        <VariableNameTH>Name</VariableNameTH>
                        <VariableValueTH colSpan={2}>Value</VariableValueTH>
                      </TR>
                    </thead>
                    <tbody>
                      {variables.map((variable) => {
                        return (
                          <TR key={variable.name}>
                            {canCompleteTask ? (
                              <>
                                <RowTH>
                                  <label htmlFor={variable.name}>
                                    {variable.name}
                                  </label>
                                </RowTH>
                                <ValueInputTD>
                                  <Field
                                    name={createVariableFieldName(
                                      variable.name,
                                    )}
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
                                  {form.getFieldState(
                                    createVariableFieldName(variable.name),
                                  )?.error !== undefined && (
                                    <Warning
                                      title={
                                        form.getFieldState(
                                          createVariableFieldName(
                                            variable.name,
                                          ),
                                        )?.error
                                      }
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
                      {canCompleteTask && (
                        <FieldArray name="newVariables">
                          {({fields}) =>
                            fields.map((variable, index) => {
                              const error = getError(
                                form.getFieldState(`${variable}.name`),
                                form.getFieldState(`${variable}.value`),
                              );
                              return (
                                <TR key={variable} data-testid={variable}>
                                  <NameInputTD>
                                    <Field name={`${variable}.name`}>
                                      {({input, meta}) => (
                                        <NameInput
                                          {...input}
                                          placeholder="Name"
                                          aria-label={`New variable ${index} name`}
                                          aria-invalid={
                                            meta.error !== undefined &&
                                            isVariableDirty(
                                              form.getFieldState(
                                                `${variable}.name`,
                                              ),
                                              form.getFieldState(
                                                `${variable}.value`,
                                              ),
                                            )
                                          }
                                        />
                                      )}
                                    </Field>
                                  </NameInputTD>
                                  <ValueInputTD>
                                    <Field name={`${variable}.value`}>
                                      {({input, meta}) => (
                                        <EditTextarea
                                          {...input}
                                          aria-label={`New variable ${index} value`}
                                          placeholder="Value"
                                          aria-invalid={
                                            meta.error !== undefined &&
                                            isVariableDirty(
                                              form.getFieldState(
                                                `${variable}.name`,
                                              ),
                                              form.getFieldState(
                                                `${variable}.value`,
                                              ),
                                            )
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
              ) : (
                <EmptyMessage>Task has no Variables</EmptyMessage>
              )}
            </Body>
          </Container>
          {canCompleteTask && (
            <DetailsFooter>
              <Button
                type="submit"
                disabled={
                  form.getState().submitting ||
                  form.getState().hasValidationErrors
                }
              >
                Complete Task
              </Button>
            </DetailsFooter>
          )}
        </StyledForm>
      )}
    </Form>
  );
};
export {Variables};
