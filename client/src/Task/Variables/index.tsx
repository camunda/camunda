/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef, useState} from 'react';
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
  CreateButton,
  Plus,
  Cross,
  InputTD,
  VariableNameTH,
  VariableValueTH,
  IconTD,
  IconContainer,
  IconButton,
  RowTH,
  Form as StyledForm,
  ValueContainer,
  ValueTextField,
  NameTextField,
} from './styled';
import {
  validateNameCharacters,
  validateNameComplete,
  validateNameNotDuplicate,
  validateValueComplete,
  validateValueJSON,
} from './validators';
import {mergeValidators} from './validators/mergeValidators';
import {
  createVariableFieldName,
  createNewVariableFieldName,
} from './createVariableFieldName';
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
import {LoadingTextarea} from './LoadingTextarea';
import {usePermissions} from 'modules/hooks/usePermissions';
import {OnNewVariableAdded} from './OnNewVariableAdded';
import {JSONEditorModal} from './JSONEditorModal';

type Props = {
  onSubmit: (variables: Pick<Variable, 'name' | 'value'>[]) => Promise<void>;
  task: GetTask['task'];
};

const Variables: React.FC<Props> = ({onSubmit, task}) => {
  const tableContainer = useRef<HTMLDivElement>(null);
  const {hasPermission} = usePermissions(['write']);
  const {data: userData, loading} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const {
    variables,
    loading: areVariablesLoading,
    queryFullVariable,
    variablesLoadingFullValue,
  } = useTaskVariables(task.id);
  const [editingVariable, setEditingVariable] = useState<string | void>();

  if (loading || areVariablesLoading) {
    return null;
  }

  const {assignee, taskState} = task;
  const canCompleteTask =
    userData?.currentUser.userId === assignee &&
    taskState === 'CREATED' &&
    hasPermission;

  const isVariableDirty = (
    name: undefined | FieldState<string>,
    value: undefined | FieldState<string>,
  ): boolean => {
    return Boolean(name?.dirty || value?.dirty);
  };

  const hasEmptyNewVariable = (values: FormValues) =>
    values.newVariables?.some((variable) => variable === undefined);

  return (
    <Form<FormValues>
      mutators={{...arrayMutators}}
      onSubmit={async (values, form) => {
        const {dirtyFields, initialValues = []} = form.getState();

        const existingVariables = intersection(
          Object.keys(initialValues),
          Object.keys(dirtyFields),
        ).map((name) => ({
          name,
          value: values[name],
        }));
        const newVariables = get(values, 'newVariables') || [];

        await onSubmit([
          ...existingVariables.map((variable) => ({
            ...variable,
            name: getVariableFieldName(variable.name),
          })),
          ...newVariables,
        ]);
      }}
      initialValues={variables.reduce(
        (values, variable) => ({
          ...values,
          [createVariableFieldName(variable.name)]: variable.value,
        }),
        {},
      )}
      keepDirtyOnReinitialize
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
                                <InputTD>
                                  <Field
                                    name={createVariableFieldName(
                                      variable.name,
                                    )}
                                    validate={
                                      variable.isValueTruncated
                                        ? () => undefined
                                        : validateValueJSON
                                    }
                                  >
                                    {({input, meta}) => (
                                      <LoadingTextarea
                                        name={input.name}
                                        data-testid={`variable-value-${input.name}`}
                                        onChange={input.onChange}
                                        value={input.value}
                                        error={meta.error}
                                        isLoading={variablesLoadingFullValue.includes(
                                          variable.id,
                                        )}
                                        id={variable.name}
                                        onFocus={() => {
                                          if (variable.isValueTruncated) {
                                            queryFullVariable(variable.id);
                                          }
                                        }}
                                        fieldSuffix={{
                                          type: 'icon',
                                          icon: 'window',
                                          press: () => {
                                            if (variable.isValueTruncated) {
                                              queryFullVariable(variable.id);
                                            }
                                            setEditingVariable(input.name);
                                          },
                                          tooltip: 'Open JSON editor modal',
                                        }}
                                      />
                                    )}
                                  </Field>
                                </InputTD>
                                <IconTD />
                              </>
                            ) : (
                              <>
                                <RowTH>{variable.name}</RowTH>
                                <TD>
                                  <ValueContainer>
                                    {`${variable.value}${
                                      variable.isValueTruncated ? '...' : ''
                                    }`}
                                  </ValueContainer>
                                </TD>
                              </>
                            )}
                          </TR>
                        );
                      })}
                      {canCompleteTask && (
                        <>
                          <OnNewVariableAdded
                            name="newVariables"
                            execute={() => {
                              const element = tableContainer.current;
                              if (element !== null) {
                                element.scrollTop = element.scrollHeight;
                              }
                            }}
                          />
                          <FieldArray name="newVariables">
                            {({fields}) =>
                              fields.map((variable, index) => {
                                return (
                                  <TR key={variable} data-testid={variable}>
                                    <InputTD>
                                      <Field
                                        name={createNewVariableFieldName(
                                          variable,
                                          'name',
                                        )}
                                        validate={mergeValidators(
                                          validateNameCharacters,
                                          validateNameComplete,
                                          validateNameNotDuplicate,
                                        )}
                                      >
                                        {({input, meta}) => (
                                          <NameTextField
                                            {...input}
                                            type="text"
                                            name={input.name}
                                            data-testid={input.name}
                                            onChange={input.onChange}
                                            value={input.value}
                                            aria-label={`New variable ${index} name`}
                                            error={meta.error}
                                            placeholder="Name"
                                            autoFocus={true}
                                            shouldDebounceError={
                                              !meta.dirty &&
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
                                    </InputTD>
                                    <InputTD>
                                      <Field
                                        name={createNewVariableFieldName(
                                          variable,
                                          'value',
                                        )}
                                        validate={validateValueComplete}
                                      >
                                        {({input, meta}) => (
                                          <ValueTextField
                                            {...input}
                                            type="text"
                                            name={input.name}
                                            data-testid={`${input.name}`}
                                            onChange={input.onChange}
                                            value={input.value}
                                            aria-label={`New variable ${index} value`}
                                            error={meta.error}
                                            placeholder="Value"
                                            fieldSuffix={{
                                              type: 'icon',
                                              icon: 'window',
                                              press: () => {
                                                setEditingVariable(
                                                  `${variable}.value`,
                                                );
                                              },
                                              tooltip: 'Open JSON editor modal',
                                            }}
                                            shouldDebounceError={
                                              !meta.dirty &&
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
                                    </InputTD>
                                    <IconTD>
                                      <IconContainer>
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
                        </>
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
                  form.getState().hasValidationErrors ||
                  form.getState().validating ||
                  hasEmptyNewVariable(values)
                }
              >
                Complete Task
              </Button>
            </DetailsFooter>
          )}
          {editingVariable ? (
            <JSONEditorModal
              title="Edit Variable"
              onClose={() => {
                setEditingVariable(undefined);
              }}
              onSave={(value) => {
                form.change(editingVariable, value);
                setEditingVariable(undefined);
              }}
              value={get(values, editingVariable)}
            />
          ) : null}
        </StyledForm>
      )}
    </Form>
  );
};
export {Variables};
