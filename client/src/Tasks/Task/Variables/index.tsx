/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useRef, useState} from 'react';
import {Field, Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import intersection from 'lodash/intersection';
import get from 'lodash/get';
import arrayMutators from 'final-form-arrays';
import {
  Table,
  LeftTD,
  RightTD,
  TH,
  TR,
  ScrollableContent,
} from 'modules/components/Table';
import {
  Container,
  Body,
  TableContainer,
  EmptyMessage,
  IconContainer,
  Form as StyledForm,
  EmptyFieldsInformationIcon,
} from './styled';
import {
  validateNameCharacters,
  validateNameComplete,
  validateDuplicateNames,
  validateValueComplete,
  validateValueJSON,
} from './validators';
import {mergeValidators} from './validators/mergeValidators';
import {
  createVariableFieldName,
  createNewVariableFieldName,
} from './createVariableFieldName';
import {getVariableFieldName} from './getVariableFieldName';
import {Variable, User} from 'modules/types';
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
import {TextInput} from './TextInput';
import {IconButton} from './IconButton';
import {DelayedErrorField} from 'Tasks/Task/DelayedErrorField';
import {Button, InlineLoadingStatus, Layer} from '@carbon/react';
import {Information, Close, Popup, Add} from '@carbon/react/icons';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {getCompletionButtonDescription} from 'modules/utils/getCompletionButtonDescription';

const CODE_EDITOR_BUTTON_TOOLTIP_LABEL = 'Open JSON code editor';

function variableIndexToOrdinal(numberValue: number): string {
  const realOrderIndex = (numberValue + 1).toString();

  if (['11', '12', '13'].includes(realOrderIndex.slice(-2))) {
    return `${realOrderIndex}th`;
  }

  switch (realOrderIndex.slice(-1)) {
    case '1':
      return `${realOrderIndex}st`;
    case '2':
      return `${realOrderIndex}nd`;
    case '3':
      return `${realOrderIndex}rd`;
    default:
      return `${realOrderIndex}th`;
  }
}

type Props = {
  onSubmit: (variables: Pick<Variable, 'name' | 'value'>[]) => Promise<void>;
  onSubmitSuccess: () => void;
  onSubmitFailure: (error: Error) => void;
  task: GetTask['task'];
  user: User;
};

const Variables: React.FC<Props> = ({
  onSubmit,
  task,
  onSubmitSuccess,
  onSubmitFailure,
  user,
}) => {
  const tableContainer = useRef<HTMLDivElement>(null);
  const {hasPermission} = usePermissions(['write']);
  const {queryFullVariable, variablesLoadingFullValue, variables, loading} =
    useTaskVariables(task.id);
  const [editingVariable, setEditingVariable] = useState<string | undefined>();
  const [submissionState, setSubmissionState] =
    useState<InlineLoadingStatus>('inactive');
  const isModalOpen = editingVariable !== undefined;

  const {assignee, taskState} = task;
  const canCompleteTask =
    user.userId === assignee && taskState === 'CREATED' && hasPermission;

  const hasEmptyNewVariable = (values: FormValues) =>
    values.newVariables?.some((variable) => variable === undefined);

  if (loading) {
    return null;
  }

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

        try {
          setSubmissionState('active');
          await onSubmit([
            ...existingVariables.map((variable) => ({
              ...variable,
              name: getVariableFieldName(variable.name),
            })),
            ...newVariables,
          ]);

          setSubmissionState('finished');
        } catch (error) {
          onSubmitFailure(error as Error);
          setSubmissionState('error');
        }
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
      {({
        form,
        handleSubmit,
        values,
        validating,
        submitting,
        hasValidationErrors,
      }) => (
        <Layer
          as={StyledForm}
          onSubmit={handleSubmit}
          hasFooter={canCompleteTask}
          data-testid="variables-table"
        >
          <ResetForm isAssigned={canCompleteTask} />
          <Container>
            <PanelHeader>
              <PanelTitle>Variables</PanelTitle>
              {canCompleteTask && (
                <Button
                  kind="ghost"
                  type="button"
                  size="sm"
                  onClick={() => {
                    form.mutators.push('newVariables');
                  }}
                  renderIcon={Add}
                >
                  Add Variable
                </Button>
              )}
            </PanelHeader>
            <Body>
              {variables.length >= 1 ||
              (values?.newVariables?.length !== undefined &&
                values?.newVariables?.length >= 1) ? (
                <>
                  <Table>
                    <thead>
                      <TR $hideBorders>
                        <TH>Name</TH>
                        <TH>Value</TH>
                      </TR>
                    </thead>
                  </Table>
                  <TableContainer
                    ref={tableContainer}
                    data-testid="variables-form-table"
                  >
                    <Table>
                      <tbody>
                        {variables.map((variable) => {
                          return (
                            <TR key={variable.name}>
                              {canCompleteTask ? (
                                <>
                                  <LeftTD>
                                    <label
                                      htmlFor={createVariableFieldName(
                                        variable.name,
                                      )}
                                    >
                                      {variable.name}
                                    </label>
                                  </LeftTD>
                                  <RightTD
                                    suffix={
                                      <IconContainer>
                                        <IconButton
                                          label={
                                            CODE_EDITOR_BUTTON_TOOLTIP_LABEL
                                          }
                                          onClick={() => {
                                            if (variable.isValueTruncated) {
                                              queryFullVariable(variable.id);
                                            }

                                            setEditingVariable(
                                              createVariableFieldName(
                                                variable.name,
                                              ),
                                            );
                                          }}
                                        >
                                          <Popup />
                                        </IconButton>
                                      </IconContainer>
                                    }
                                  >
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
                                          {...input}
                                          id={input.name}
                                          invalidText={meta.error}
                                          isLoading={variablesLoadingFullValue.includes(
                                            variable.id,
                                          )}
                                          onFocus={(event) => {
                                            if (variable.isValueTruncated) {
                                              queryFullVariable(variable.id);
                                            }
                                            input.onFocus(event);
                                          }}
                                          isActive={meta.active}
                                          type="text"
                                          labelText={`${variable.name} value`}
                                          placeholder={`${variable.name} value`}
                                        />
                                      )}
                                    </Field>
                                  </RightTD>
                                </>
                              ) : (
                                <>
                                  <LeftTD>{variable.name}</LeftTD>
                                  <RightTD>
                                    <ScrollableContent>
                                      {`${variable.value}${
                                        variable.isValueTruncated ? '...' : ''
                                      }`}
                                    </ScrollableContent>
                                  </RightTD>
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
                                    <TR key={variable}>
                                      <LeftTD>
                                        <DelayedErrorField
                                          name={createNewVariableFieldName(
                                            variable,
                                            'name',
                                          )}
                                          validate={mergeValidators(
                                            validateNameCharacters,
                                            validateNameComplete,
                                            validateDuplicateNames,
                                          )}
                                          addExtraDelay={Boolean(
                                            !form.getFieldState(
                                              `${variable}.name`,
                                            )?.dirty &&
                                              form.getFieldState(
                                                `${variable}.value`,
                                              )?.dirty,
                                          )}
                                        >
                                          {({input, meta}) => (
                                            <TextInput
                                              {...input}
                                              id={input.name}
                                              invalidText={meta.error}
                                              type="text"
                                              labelText={`${variableIndexToOrdinal(
                                                index,
                                              )} variable name`}
                                              placeholder="Name"
                                              autoFocus
                                            />
                                          )}
                                        </DelayedErrorField>
                                      </LeftTD>
                                      <RightTD
                                        suffix={
                                          <IconContainer>
                                            <IconButton
                                              label={
                                                CODE_EDITOR_BUTTON_TOOLTIP_LABEL
                                              }
                                              onClick={() => {
                                                setEditingVariable(
                                                  `${variable}.value`,
                                                );
                                              }}
                                            >
                                              <Popup />
                                            </IconButton>
                                            <IconButton
                                              label={`Remove ${variableIndexToOrdinal(
                                                index,
                                              )} new variable`}
                                              onClick={() => {
                                                fields.remove(index);
                                              }}
                                            >
                                              <Close />
                                            </IconButton>
                                          </IconContainer>
                                        }
                                      >
                                        <DelayedErrorField
                                          name={createNewVariableFieldName(
                                            variable,
                                            'value',
                                          )}
                                          validate={validateValueComplete}
                                          addExtraDelay={Boolean(
                                            form.getFieldState(
                                              `${variable}.name`,
                                            )?.dirty &&
                                              !form.getFieldState(
                                                `${variable}.value`,
                                              )?.dirty,
                                          )}
                                        >
                                          {({input, meta}) => (
                                            <TextInput
                                              {...input}
                                              id={input.name}
                                              type="text"
                                              labelText={`${variableIndexToOrdinal(
                                                index,
                                              )} variable value`}
                                              invalidText={meta.error}
                                              placeholder="Value"
                                            />
                                          )}
                                        </DelayedErrorField>
                                      </RightTD>
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
                </>
              ) : (
                <EmptyMessage>Task has no Variables</EmptyMessage>
              )}
            </Body>
          </Container>
          {(canCompleteTask || submissionState === 'finished') && (
            <DetailsFooter>
              {hasEmptyNewVariable(values) && (
                <EmptyFieldsInformationIcon
                  label="You first have to fill all fields"
                  align="top"
                >
                  <Information size={20} />
                </EmptyFieldsInformationIcon>
              )}

              <AsyncActionButton
                inlineLoadingProps={{
                  description: getCompletionButtonDescription(submissionState),
                  'aria-live': ['error', 'finished'].includes(submissionState)
                    ? 'assertive'
                    : 'polite',
                  onSuccess: () => {
                    setSubmissionState('inactive');
                    onSubmitSuccess();
                  },
                }}
                buttonProps={{
                  size: 'md',
                  type: 'submit',
                  disabled:
                    submitting ||
                    hasValidationErrors ||
                    validating ||
                    hasEmptyNewVariable(values),
                }}
                status={submissionState}
                onError={() => {
                  setSubmissionState('inactive');
                }}
              >
                Complete Task
              </AsyncActionButton>
            </DetailsFooter>
          )}

          <JSONEditorModal
            isOpen={isModalOpen}
            title="Edit Variable"
            onClose={() => {
              setEditingVariable(undefined);
            }}
            onSave={(value) => {
              if (isModalOpen) {
                form.change(editingVariable, value);
                setEditingVariable(undefined);
              }
            }}
            value={isModalOpen ? get(values, editingVariable) : ''}
          />
        </Layer>
      )}
    </Form>
  );
};
export {Variables};
