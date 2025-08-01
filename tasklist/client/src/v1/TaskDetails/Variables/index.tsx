/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, lazy, useRef, useState} from 'react';
import {Form} from 'react-final-form';
import intersection from 'lodash/intersection';
import get from 'lodash/get';
import arrayMutators from 'final-form-arrays';
import {match, Pattern} from 'ts-pattern';
import {Button, Heading, type InlineLoadingProps, Layer} from '@carbon/react';
import {Information, Add} from '@carbon/react/icons';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import type {Variable, Task} from 'v1/api/types';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/8.8';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'common/tasks/details/TaskDetailsLayout';
import {Separator} from 'common/tasks/variables-editor/Separator';
import {useAllVariables} from 'v1/api/useAllVariables.query';
import {useTranslation} from 'react-i18next';
import {FailedVariableFetchError} from 'common/tasks/details/FailedVariableFetchError';
import {CompleteTaskButton} from 'common/tasks/details/CompleteTaskButton';
import {createVariableFieldName} from 'common/tasks/variables-editor/createVariableFieldName';
import {getVariableFieldName} from 'common/tasks/variables-editor/getVariableFieldName';
import {VariableEditor} from 'v1/TaskDetails/Variables/VariableEditor';
import {IconButton} from 'common/tasks/variables-editor/IconButton';
import {ResetForm} from 'common/tasks/variables-editor/ResetForm';
import type {FormValues} from 'common/tasks/variables-editor/types';
import styles from 'common/tasks/variables-editor/styles.module.scss';
import cn from 'classnames';
import {completionErrorMap} from 'v1/api/useCompleteTask.mutation';

const JSONEditorModal = lazy(async () => {
  const [{loadMonaco}, {JSONEditorModal}] = await Promise.all([
    import('common/loadMonaco'),
    import('common/tasks/variables-editor/JSONEditorModal'),
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
  const {t} = useTranslation();
  const {
    data,
    isLoading,
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
    useState<NonNullable<InlineLoadingProps['status']>>('inactive');
  const canCompleteTask =
    user.userId === assignee && taskState === 'CREATED' && status === 'success';
  const hasEmptyNewVariable = (values: FormValues | undefined) =>
    values?.newVariables?.some((variable) => variable === undefined);
  const variables = data ?? [];
  const isJsonEditorModalOpen = editingVariable !== undefined;

  if (isLoading) {
    return null;
  }

  return (
    <Form<FormValues>
      mutators={{...arrayMutators}}
      onSubmit={async (values, form) => {
        const {dirtyFields = [], initialValues = []} = form.getState();

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
          if (
            error instanceof Error &&
            error.name === completionErrorMap.taskProcessingTimeout
          ) {
            onSubmitFailure(error);
            return;
          }
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
          <div className={styles.panelHeader}>
            <Heading>{t('variablesTitle')}</Heading>
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
                  canCompleteTask ? undefined : t('variablesAddVariableTooltip')
                }
              >
                {t('taskVariablesAddVariable')}
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
                  newVariablesLength: values?.newVariables?.length ?? 0,
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
                          heading={t('variablesNoVariablesHeading')}
                          description={
                            taskState === 'COMPLETED'
                              ? ''
                              : t('variablesClickOnAddVariablesPrompt')
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

                <div className={styles.footer}>
                  {hasEmptyNewVariable(values) && (
                    <IconButton
                      className={styles.inlineIcon}
                      label={t('variablesFillAllFieldsWarning')}
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
                    isHidden={taskState === 'COMPLETED'}
                    isDisabled={
                      submitting ||
                      hasValidationErrors ||
                      validating ||
                      hasEmptyNewVariable(values) ||
                      !canCompleteTask
                    }
                  />
                </div>
              </TaskDetailsContainer>

              <Suspense>
                <JSONEditorModal
                  isOpen={isJsonEditorModalOpen}
                  title={t('jsonEditorEditVariableTitle')}
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
