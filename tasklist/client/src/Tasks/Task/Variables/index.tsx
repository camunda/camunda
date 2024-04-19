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
import {Form} from 'react-final-form';
import intersection from 'lodash/intersection';
import get from 'lodash/get';
import arrayMutators from 'final-form-arrays';
import {match, Pattern} from 'ts-pattern';
import {Button, InlineLoadingStatus, Heading, Layer} from '@carbon/react';
import {Information, Add} from '@carbon/react/icons';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import {Variable, CurrentUser, Task} from 'modules/types';
import {DetailsFooter} from 'modules/components/DetailsFooter';
import {usePermissions} from 'modules/hooks/usePermissions';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'modules/components/TaskDetailsLayout';
import {Separator} from 'modules/components/Separator';
import {useAllVariables} from 'modules/queries/useAllVariables';
import {FailedVariableFetchError} from 'modules/components/FailedVariableFetchError';
import {CompleteTaskButton} from 'modules/components/CompleteTaskButton';
import {createVariableFieldName} from './createVariableFieldName';
import {getVariableFieldName} from './getVariableFieldName';
import {VariableEditor} from './VariableEditor';
import {IconButton} from './IconButton';
import {ResetForm} from './ResetForm';
import {FormValues} from './types';
import styles from './styles.module.scss';

const JSONEditorModal = lazy(async () => {
  const [{loadMonaco}, {JSONEditorModal}] = await Promise.all([
    import('loadMonaco'),
    import('./JSONEditorModal'),
  ]);

  loadMonaco();

  return {default: JSONEditorModal};
});

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
                        <VariableEditor
                          containerRef={formRef}
                          variables={variables}
                          readOnly={!canCompleteTask}
                          fetchFullVariable={fetchFullVariable}
                          variablesLoadingFullValue={variablesLoadingFullValue}
                          onEdit={(id) => setEditingVariable(id)}
                        />
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

                  <CompleteTaskButton
                    submissionState={submissionState}
                    onSuccess={() => {
                      setSubmissionState('inactive');
                      onSubmitSuccess();
                    }}
                    onError={() => {
                      setSubmissionState('inactive');
                    }}
                    hide={taskState === 'COMPLETED'}
                    disabled={
                      submitting ||
                      hasValidationErrors ||
                      validating ||
                      hasEmptyNewVariable(values) ||
                      !canCompleteTask
                    }
                  />
                </DetailsFooter>
              </TaskDetailsContainer>

              <Suspense>
                <JSONEditorModal
                  isOpen={editingVariable !== undefined}
                  title="Edit Variable"
                  onClose={() => {
                    setEditingVariable(undefined);
                  }}
                  onSave={(value) => {
                    if (editingVariable !== undefined) {
                      form.change(editingVariable, value);
                      setEditingVariable(undefined);
                    }
                  }}
                  value={
                    editingVariable !== undefined
                      ? get(values, editingVariable)
                      : ''
                  }
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
