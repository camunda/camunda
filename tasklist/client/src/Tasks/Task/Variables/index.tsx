/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, lazy, useRef, useState} from 'react';
import {Form} from 'react-final-form';
import get from 'lodash/get';
import arrayMutators from 'final-form-arrays';
import {match, Pattern} from 'ts-pattern';
import {Button, InlineLoadingStatus, Heading, Layer} from '@carbon/react';
import {Information, Add} from '@carbon/react/icons';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import {Variable, CurrentUser, Task} from 'modules/types';
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
import {getVariableFieldName} from './getVariableFieldName';
import {VariableEditor} from './VariableEditor';
import {IconButton} from './IconButton';
import {ResetForm} from './ResetForm';
import {FormValues} from './types';
import styles from './styles.module.scss';
import cn from 'classnames';
import {SaveButton} from 'modules/components/SaveButton';
import {FormState} from 'final-form';
import {createVariableFieldName} from './createVariableFieldName';
import {useSaveButton} from 'modules/hooks/useSaveButton';
import {DetailsFooter} from 'modules/components/DetailsFooter';

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

function createInitialFormValuesFromVariables(variables: Variable[]) {
  const entries: Array<[string, string | null]> = [];
  const newVariables: Array<Pick<Variable, 'name' | 'value'>> = [];

  variables.forEach(({value, draft, name, previewValue}) => {
    if (draft === null) {
      entries.push([createVariableFieldName(name), value ?? previewValue]);
    } else if (value === null) {
      newVariables.push({
        name: name,
        value: draft.value ?? draft.previewValue,
      });
    } else {
      entries.push([
        createVariableFieldName(name),
        draft.value ?? draft.previewValue,
      ]);
    }
  });

  return Object.fromEntries([...entries, ['newVariables', newVariables]]);
}

function extractVariablesFromFormState(
  formState: FormState<FormValues, Partial<FormValues>>,
) {
  const {dirtyFields, values} = formState;

  const dirtyVariables = Object.keys(dirtyFields)
    .filter((key) => !key.startsWith('newVariables'))
    .filter((key) => dirtyFields[key])
    .map((key) => ({
      name: getVariableFieldName(key),
      value: values[key],
    }));

  const newVariables = get(values, 'newVariables') || [];

  return [...dirtyVariables, ...newVariables];
}

function hasEmptyNewVariable(values: FormValues) {
  return values.newVariables?.some((variable) => variable === undefined);
}

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
    isLoading,
    fetchFullVariable,
    variablesLoadingFullValue,
    status,
    refetch: refetchVariables,
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
  const variables = data ?? [];
  const isJsonEditorModalOpen = editingVariable !== undefined;
  const {save, savingState} = useSaveButton(task.id);

  if (isLoading) {
    return null;
  }

  return (
    <Form<FormValues>
      mutators={{...arrayMutators}}
      onSubmit={async (_, form) => {
        try {
          setSubmissionState('active');
          await onSubmit(extractVariablesFromFormState(form.getState()));
          setSubmissionState('finished');
        } catch (error) {
          onSubmitFailure(error as Error);
          setSubmissionState('error');
        }
      }}
      initialValues={createInitialFormValuesFromVariables(variables)}
      keepDirtyOnReinitialize
    >
      {({
        form,
        handleSubmit,
        values,
        validating,
        submitting,
        hasValidationErrors,
        dirty,
      }) => (
        <>
          <div className={styles.panelHeader}>
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
          </div>
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
                      <Layer className={cn(styles.container, styles.gutter)}>
                        <C3EmptyState
                          heading="Task has no variables"
                          description={
                            taskState === 'COMPLETED'
                              ? ''
                              : 'Click on Add Variable'
                          }
                        />
                      </Layer>
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
                      <Layer
                        className={styles.container}
                        data-testid="variables-form-table"
                      >
                        <VariableEditor
                          containerRef={formRef}
                          variables={variables}
                          readOnly={!canCompleteTask}
                          fetchFullVariable={fetchFullVariable}
                          variablesLoadingFullValue={variablesLoadingFullValue}
                          onEdit={(id) => setEditingVariable(id)}
                        />
                      </Layer>
                    ),
                  )
                  .otherwise(() => null)}

                <SaveButton status={savingState}>
                  {({SaveDraftButton, Status}) => (
                    <DetailsFooter
                      className={cn(styles.actionBar, styles.footer)}
                      status={<Status />}
                    >
                      {hasEmptyNewVariable(values) && (
                        <IconButton
                          className={styles.inlineIcon}
                          label="You first have to fill all fields"
                          align="top"
                        >
                          <Information size={20} />
                        </IconButton>
                      )}

                      <SaveDraftButton
                        savingState={savingState}
                        onClick={async () => {
                          await save(
                            extractVariablesFromFormState(form.getState()),
                          );
                          refetchVariables();
                        }}
                        isHidden={!canCompleteTask}
                        isDisabled={
                          !dirty ||
                          savingState === 'active' ||
                          submitting ||
                          hasValidationErrors ||
                          validating ||
                          hasEmptyNewVariable(values) ||
                          !canCompleteTask
                        }
                      />

                      <CompleteTaskButton
                        submissionState={submissionState}
                        onSuccess={() => {
                          setSubmissionState('inactive');
                          onSubmitSuccess();
                        }}
                        onError={() => {
                          setSubmissionState('inactive');
                        }}
                        isHidden={taskState === 'COMPLETED'}
                        isDisabled={
                          submitting ||
                          hasValidationErrors ||
                          validating ||
                          hasEmptyNewVariable(values) ||
                          !canCompleteTask
                        }
                      />
                    </DetailsFooter>
                  )}
                </SaveButton>
              </TaskDetailsContainer>

              <Suspense>
                <JSONEditorModal
                  isOpen={isJsonEditorModalOpen}
                  title="Edit Variable"
                  onClose={() => {
                    setEditingVariable(undefined);
                  }}
                  onSave={(value) => {
                    if (isJsonEditorModalOpen) {
                      form.change(editingVariable, value);
                      setEditingVariable(undefined);
                    }
                  }}
                  value={
                    isJsonEditorModalOpen ? get(values, editingVariable) : ''
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
