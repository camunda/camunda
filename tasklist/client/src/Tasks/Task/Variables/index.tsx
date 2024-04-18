/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Suspense, lazy, useRef, useState} from 'react';
import {Field, Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import intersection from 'lodash/intersection';
import get from 'lodash/get';
import arrayMutators from 'final-form-arrays';
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
  Layer,
  StructuredListWrapper,
  StructuredListCell,
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
import {useAllVariables} from 'modules/queries/useAllVariables';
import {match, Pattern} from 'ts-pattern';
import {FailedVariableFetchError} from 'modules/components/FailedVariableFetchError';
import styles from './styles.module.scss';
import cn from 'classnames';

const JSONEditorModal = lazy(async () => {
  const [{loadMonaco}, {JSONEditorModal}] = await Promise.all([
    import('loadMonaco'),
    import('./JSONEditorModal'),
  ]);

  loadMonaco();

  return {default: JSONEditorModal};
});

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
  const {
    data,
    isInitialLoading,
    fetchFullVariable,
    variablesLoadingFullValue,
    status,
  } = useAllVariables(
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
    user.userId === assignee &&
    taskState === 'CREATED' &&
    hasPermission &&
    status === 'success';
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
          <TaskDetailsRow className={styles.panelHeader}>
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
          </TaskDetailsRow>
          <Separator />
          <ScrollableContent>
            <form
              className={styles.form}
              onSubmit={handleSubmit}
              data-testid="variables-table"
              ref={formRef}
            >
              <ResetForm isAssigned={canCompleteTask} />

              <TaskDetailsContainer tabIndex={-1}>
                {match({
                  variablesLength: variables.length,
                  newVariablesLength: values.newVariables?.length ?? 0,
                  status,
                })
                  .with(
                    {
                      variablesLength: Pattern.number.lte(0),
                      newVariablesLength: Pattern.number.lte(0),
                      status: Pattern.union('pending', 'success'),
                    },
                    () => (
                      <TaskDetailsRow as={Layer}>
                        <C3EmptyState
                          heading="Task has no variables"
                          description={
                            taskState === 'COMPLETED'
                              ? ''
                              : 'Click on Add Variable'
                          }
                        />
                      </TaskDetailsRow>
                    ),
                  )
                  .with(
                    {
                      status: 'error',
                    },
                    () => (
                      <TaskDetailsRow>
                        <FailedVariableFetchError />
                      </TaskDetailsRow>
                    ),
                  )
                  .with(
                    Pattern.union(
                      {
                        variablesLength: Pattern.number.gte(1),
                        status: 'success',
                      },
                      {
                        newVariablesLength: Pattern.number.gte(1),
                        status: 'success',
                      },
                    ),
                    () => (
                      <TaskDetailsRow
                        className={styles.container}
                        data-testid="variables-form-table"
                        as={Layer}
                        $disabledSidePadding
                      >
                        <StructuredListWrapper
                          className={styles.list}
                          isCondensed
                        >
                          <StructuredListHead>
                            <StructuredListRow head>
                              <StructuredListCell
                                className={styles.listCell}
                                head
                              >
                                Name
                              </StructuredListCell>
                              <StructuredListCell
                                className={styles.listCell}
                                head
                              >
                                Value
                              </StructuredListCell>
                              <StructuredListCell
                                className={styles.listCell}
                                head
                              />
                            </StructuredListRow>
                          </StructuredListHead>
                          <StructuredListBody>
                            {variables.map((variable) =>
                              canCompleteTask ? (
                                <StructuredListRow key={variable.name}>
                                  <StructuredListCell
                                    className={cn(
                                      styles.listCell,
                                      styles.cellName,
                                    )}
                                  >
                                    <label
                                      htmlFor={createVariableFieldName(
                                        variable.name,
                                      )}
                                    >
                                      {variable.name}
                                    </label>
                                  </StructuredListCell>
                                  <StructuredListCell
                                    className={cn(
                                      styles.listCell,
                                      styles.valueCell,
                                    )}
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
                                  </StructuredListCell>
                                  <StructuredListCell
                                    className={cn(
                                      styles.listCell,
                                      styles.controlsCell,
                                    )}
                                  >
                                    <div
                                      className={cn(
                                        styles.iconButtons,
                                        styles.extraPadding,
                                      )}
                                    >
                                      <IconButton
                                        label={CODE_EDITOR_BUTTON_TOOLTIP_LABEL}
                                        onClick={() => {
                                          if (variable.isValueTruncated) {
                                            fetchFullVariable(variable.id);
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
                                    </div>
                                  </StructuredListCell>
                                </StructuredListRow>
                              ) : (
                                <StructuredListRow key={variable.name}>
                                  <StructuredListCell
                                    className={cn(
                                      styles.listCell,
                                      styles.cellName,
                                    )}
                                  >
                                    {variable.name}
                                  </StructuredListCell>
                                  <StructuredListCell
                                    className={cn(
                                      styles.listCell,
                                      styles.valueCell,
                                    )}
                                  >
                                    <div className={styles.scrollableOuter}>
                                      <div className={styles.scrollableInner}>
                                        {variable.isValueTruncated
                                          ? `${variable.previewValue}...`
                                          : variable.value}
                                      </div>
                                    </div>
                                  </StructuredListCell>
                                  <StructuredListCell
                                    className={cn(
                                      styles.listCell,
                                      styles.controlsCell,
                                    )}
                                  />
                                </StructuredListRow>
                              ),
                            )}
                            {canCompleteTask ? (
                              <>
                                <OnNewVariableAdded
                                  name="newVariables"
                                  execute={() => {
                                    const element =
                                      formRef.current?.parentElement;
                                    if (element) {
                                      element.scrollTop = element.scrollHeight;
                                    }
                                  }}
                                />
                                <FieldArray name="newVariables">
                                  {({fields}) =>
                                    fields.map((variable, index) => (
                                      <StructuredListRow key={variable}>
                                        <StructuredListCell
                                          className={cn(
                                            styles.listCell,
                                            styles.cellName,
                                          )}
                                        >
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
                                        </StructuredListCell>
                                        <StructuredListCell
                                          className={cn(
                                            styles.listCell,
                                            styles.valueCell,
                                          )}
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
                                        </StructuredListCell>
                                        <StructuredListCell
                                          className={cn(
                                            styles.listCell,
                                            styles.controlsCell,
                                          )}
                                        >
                                          <div className={styles.iconButtons}>
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
                                          </div>
                                        </StructuredListCell>
                                      </StructuredListRow>
                                    ))
                                  }
                                </FieldArray>
                              </>
                            ) : null}
                          </StructuredListBody>
                        </StructuredListWrapper>
                      </TaskDetailsRow>
                    ),
                  )
                  .otherwise(() => null)}

                <DetailsFooter>
                  {hasEmptyNewVariable(values) && (
                    <IconButton
                      className={styles.inlineIcon}
                      label="You first have to fill all fields"
                      align="top"
                    >
                      <Information size={20} />
                    </IconButton>
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

              <Suspense>
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
              </Suspense>
            </form>
          </ScrollableContent>
        </>
      )}
    </Form>
  );
};
export {Variables};
