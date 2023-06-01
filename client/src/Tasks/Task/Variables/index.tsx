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
  Form as StyledForm,
  EmptyFieldsInformationIcon,
  Container,
  StructuredListCell,
  StructuredListWrapper,
  ScrollableCellContent,
  IconButtonsContainer,
  VariableNameCell,
  VariableValueCell,
  ControlsCell,
  PanelHeader,
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
import {Variable, CurrentUser, Task} from 'modules/types';
import {DetailsFooter} from 'modules/components/DetailsFooter';
import {ResetForm} from './ResetForm';
import {FormValues} from './types';
import {LoadingTextarea} from './LoadingTextarea';
import {usePermissions} from 'modules/hooks/usePermissions';
import {OnNewVariableAdded} from './OnNewVariableAdded';
import {JSONEditorModal} from './JSONEditorModal';
import {TextInput} from './TextInput';
import {IconButton} from './IconButton';
import {DelayedErrorField} from 'Tasks/Task/DelayedErrorField';
import {
  Button,
  InlineLoadingStatus,
  StructuredListHead,
  StructuredListRow,
  StructuredListBody,
  Heading,
} from '@carbon/react';
import {Information, Close, Popup, Add} from '@carbon/react/icons';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {getCompletionButtonDescription} from 'modules/utils/getCompletionButtonDescription';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'modules/components/TaskDetailsLayout';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import {Separator} from 'modules/components/Separator';
import {useVariables} from 'modules/queries/useVariables';

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
  task: Task;
  user: CurrentUser;
};

const Variables: React.FC<Props> = ({
  onSubmit,
  task,
  onSubmitSuccess,
  onSubmitFailure,
  user,
}) => {
  const formRef = useRef<HTMLFormElement | null>(null);
  const {assignee, taskState} = task;
  const {hasPermission} = usePermissions(['write']);
  const {data, isInitialLoading, fetchFullVariable, variablesLoadingFullValue} =
    useVariables(
      {
        taskId: task.id,
      },
      {
        refetchOnWindowFocus: assignee === null,
        refetchOnReconnect: assignee === null,
      },
    );
  const [editingVariable, setEditingVariable] = useState<string | undefined>();
  const [submissionState, setSubmissionState] =
    useState<InlineLoadingStatus>('inactive');
  const isModalOpen = editingVariable !== undefined;
  const canCompleteTask =
    user.userId === assignee && taskState === 'CREATED' && hasPermission;
  const hasEmptyNewVariable = (values: FormValues) =>
    values.newVariables?.some((variable) => variable === undefined);
  const variables = data ?? [];

  if (isInitialLoading) {
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
          [createVariableFieldName(variable.name)]:
            variable.value ?? variable.previewValue,
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
        <>
          <PanelHeader>
            <Heading>Variables</Heading>
            {taskState !== 'COMPLETED' && (
              <Button
                kind="ghost"
                type="button"
                size="sm"
                onClick={() => {
                  form.mutators.push('newVariables');
                }}
                renderIcon={Add}
                disabled={!canCompleteTask}
                title={
                  canCompleteTask
                    ? undefined
                    : 'You must assign the task to add variables'
                }
              >
                Add Variable
              </Button>
            )}
          </PanelHeader>
          <Separator />
          <ScrollableContent>
            <StyledForm
              onSubmit={handleSubmit}
              data-testid="variables-table"
              ref={formRef}
            >
              <ResetForm isAssigned={canCompleteTask} />

              <TaskDetailsContainer tabIndex={-1}>
                {variables.length >= 1 ||
                (values?.newVariables?.length !== undefined &&
                  values?.newVariables?.length >= 1) ? (
                  <Container data-testid="variables-form-table">
                    <StructuredListWrapper isCondensed>
                      <StructuredListHead>
                        <StructuredListRow head>
                          <StructuredListCell head>Name</StructuredListCell>
                          <StructuredListCell head>Value</StructuredListCell>
                          <StructuredListCell head />
                        </StructuredListRow>
                      </StructuredListHead>
                      <StructuredListBody>
                        {variables.map((variable) =>
                          canCompleteTask ? (
                            <StructuredListRow key={variable.name}>
                              <VariableNameCell>
                                <label
                                  htmlFor={createVariableFieldName(
                                    variable.name,
                                  )}
                                >
                                  {variable.name}
                                </label>
                              </VariableNameCell>
                              <VariableValueCell>
                                <Field
                                  name={createVariableFieldName(variable.name)}
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
                                          fetchFullVariable(variable.id);
                                        }
                                        input.onFocus(event);
                                      }}
                                      isActive={meta.active}
                                      type="text"
                                      labelText={`${variable.name} value`}
                                      placeholder={`${variable.name} value`}
                                      hideLabel
                                    />
                                  )}
                                </Field>
                              </VariableValueCell>
                              <ControlsCell>
                                <IconButtonsContainer $showExtraPadding>
                                  <IconButton
                                    label={CODE_EDITOR_BUTTON_TOOLTIP_LABEL}
                                    onClick={() => {
                                      if (variable.isValueTruncated) {
                                        fetchFullVariable(variable.id);
                                      }

                                      setEditingVariable(
                                        createVariableFieldName(variable.name),
                                      );
                                    }}
                                  >
                                    <Popup />
                                  </IconButton>
                                </IconButtonsContainer>
                              </ControlsCell>
                            </StructuredListRow>
                          ) : (
                            <StructuredListRow key={variable.name}>
                              <VariableNameCell>
                                {variable.name}
                              </VariableNameCell>
                              <VariableValueCell>
                                <ScrollableCellContent>
                                  {`${variable.value}${
                                    variable.isValueTruncated ? '...' : ''
                                  }`}
                                </ScrollableCellContent>
                              </VariableValueCell>
                              <ControlsCell />
                            </StructuredListRow>
                          ),
                        )}
                        {canCompleteTask ? (
                          <>
                            <OnNewVariableAdded
                              name="newVariables"
                              execute={() => {
                                const element = formRef.current?.parentElement;
                                if (element) {
                                  element.scrollTop = element.scrollHeight;
                                }
                              }}
                            />
                            <FieldArray name="newVariables">
                              {({fields}) =>
                                fields.map((variable, index) => (
                                  <StructuredListRow key={variable}>
                                    <VariableNameCell>
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
                                    </VariableNameCell>
                                    <VariableValueCell>
                                      <DelayedErrorField
                                        name={createNewVariableFieldName(
                                          variable,
                                          'value',
                                        )}
                                        validate={validateValueComplete}
                                        addExtraDelay={Boolean(
                                          form.getFieldState(`${variable}.name`)
                                            ?.dirty &&
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
                                    </VariableValueCell>
                                    <ControlsCell>
                                      <IconButtonsContainer>
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
                                      </IconButtonsContainer>
                                    </ControlsCell>
                                  </StructuredListRow>
                                ))
                              }
                            </FieldArray>
                          </>
                        ) : null}
                      </StructuredListBody>
                    </StructuredListWrapper>
                  </Container>
                ) : (
                  <TaskDetailsRow>
                    <C3EmptyState
                      heading="Task has no variables"
                      description={
                        taskState === 'COMPLETED' ? '' : 'Click on Add Variable'
                      }
                    />
                  </TaskDetailsRow>
                )}

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
                      description:
                        getCompletionButtonDescription(submissionState),
                      'aria-live': ['error', 'finished'].includes(
                        submissionState,
                      )
                        ? 'assertive'
                        : 'polite',
                      onSuccess: () => {
                        setSubmissionState('inactive');
                        onSubmitSuccess();
                      },
                    }}
                    buttonProps={{
                      className: taskState === 'COMPLETED' ? 'hide' : '',
                      size: 'md',
                      type: 'submit',
                      disabled:
                        submitting ||
                        hasValidationErrors ||
                        validating ||
                        hasEmptyNewVariable(values) ||
                        !canCompleteTask,
                      title: canCompleteTask
                        ? undefined
                        : 'You must first assign this task to complete it',
                    }}
                    status={submissionState}
                    onError={() => {
                      setSubmissionState('inactive');
                    }}
                  >
                    Complete Task
                  </AsyncActionButton>
                </DetailsFooter>
              </TaskDetailsContainer>

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
            </StyledForm>
          </ScrollableContent>
        </>
      )}
    </Form>
  );
};
export {Variables};
