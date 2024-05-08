/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef, useState} from 'react';
import {Form, Variable, CurrentUser, Task} from 'modules/types';
import {useRemoveFormReference} from 'modules/queries/useTask';
import {getSchemaVariables} from '@bpmn-io/form-js-viewer';
import {DetailsFooter} from 'modules/components/DetailsFooter';
import {InlineLoadingStatus} from '@carbon/react';
import {usePermissions} from 'modules/hooks/usePermissions';
import {notificationsStore} from 'modules/stores/notifications';
import {FormManager} from 'modules/formManager';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'modules/components/TaskDetailsLayout';
import {useForm} from 'modules/queries/useForm';
import {useVariables} from 'modules/queries/useVariables';
import {FormJSRenderer} from 'modules/components/FormJSRenderer';
import {FailedVariableFetchError} from 'modules/components/FailedVariableFetchError';
import {Pattern, match} from 'ts-pattern';
import {CompleteTaskButton} from 'modules/components/CompleteTaskButton';

function formatVariablesToFormData(variables: Variable[]) {
  return variables.reduce(
    (accumulator, {name, value}) => ({
      ...accumulator,
      [name]: value === null ? '' : JSON.parse(value),
    }),
    {},
  );
}

function extractVariablesFromFormSchema(
  schema: string | null,
): Variable['name'][] {
  if (schema === null) {
    return [];
  }

  try {
    return getSchemaVariables(JSON.parse(schema ?? '{}'));
  } catch {
    return [];
  }
}

type Props = {
  id: Form['id'];
  processDefinitionKey: Form['processDefinitionKey'];
  task: Task;
  onSubmit: (variables: Variable[]) => Promise<void>;
  onSubmitSuccess: () => void;
  onSubmitFailure: (error: Error) => void;
  user: CurrentUser;
};

const FormJS: React.FC<Props> = ({
  id,
  processDefinitionKey,
  task,
  onSubmit,
  onSubmitSuccess,
  onSubmitFailure,
  user,
}) => {
  const formManagerRef = useRef<FormManager | null>(null);
  const [submissionState, setSubmissionState] =
    useState<InlineLoadingStatus>('inactive');
  const {assignee, taskState, formVersion} = task;
  const {data, isLoading} = useForm(
    {
      id,
      processDefinitionKey,
      version: formVersion ?? null,
    },
    {
      refetchOnReconnect: false,
      refetchOnWindowFocus: false,
    },
  );
  const {hasPermission} = usePermissions(['write']);
  const {schema} = data;
  const extractedVariables = extractVariablesFromFormSchema(schema);
  const {data: variablesData, status} = useVariables(
    {
      taskId: task.id,
      variableNames: extractedVariables,
    },
    {
      enabled: !isLoading && extractedVariables.length > 0,
      refetchOnReconnect: assignee === null,
      refetchOnWindowFocus: assignee === null,
    },
  );
  const formattedData = useMemo(
    () => formatVariablesToFormData(variablesData ?? []),
    [variablesData],
  );
  const hasFetchedVariables =
    extractedVariables.length === 0 || status === 'success';
  const canCompleteTask =
    user.userId === assignee &&
    taskState === 'CREATED' &&
    hasPermission &&
    hasFetchedVariables;
  const {removeFormReference} = useRemoveFormReference(task);

  return (
    <ScrollableContent data-testid="embedded-form" tabIndex={-1}>
      <TaskDetailsContainer>
        <TaskDetailsRow>
          {match({schema, status})
            .with(
              {
                schema: null,
              },
              () => null,
            )
            .with(
              {
                status: 'error',
              },
              () => <FailedVariableFetchError />,
            )
            .with(
              {
                schema: Pattern.not(null),
                status: Pattern.union('pending', 'success'),
              },
              ({schema}) => (
                <FormJSRenderer
                  schema={schema}
                  data={formattedData}
                  readOnly={!canCompleteTask}
                  onMount={(formManager) => {
                    formManagerRef.current = formManager;
                  }}
                  handleSubmit={onSubmit}
                  onImportError={() => {
                    removeFormReference();
                    notificationsStore.displayNotification({
                      kind: 'error',
                      title: 'Invalid Form schema',
                      isDismissable: true,
                    });
                  }}
                  onSubmitStart={() => {
                    setSubmissionState('active');
                  }}
                  onSubmitSuccess={() => {
                    setSubmissionState('finished');
                  }}
                  onSubmitError={(error) => {
                    onSubmitFailure(error as Error);
                    setSubmissionState('error');
                  }}
                  onValidationError={() => {
                    setSubmissionState('inactive');
                  }}
                />
              ),
            )
            .otherwise(() => null)}
        </TaskDetailsRow>
        <DetailsFooter>
          <CompleteTaskButton
            submissionState={submissionState}
            onClick={() => {
              setSubmissionState('active');
              formManagerRef.current?.submit();
            }}
            onSuccess={() => {
              onSubmitSuccess();
              setSubmissionState('inactive');
            }}
            onError={() => {
              setSubmissionState('inactive');
            }}
            isHidden={taskState === 'COMPLETED'}
            isDisabled={!canCompleteTask}
          />
        </DetailsFooter>
      </TaskDetailsContainer>
    </ScrollableContent>
  );
};

export {FormJS};
