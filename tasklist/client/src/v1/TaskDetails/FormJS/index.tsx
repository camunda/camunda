/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef, useState} from 'react';
import type {Form, Task} from 'v1/api/types';
import type {CurrentUser} from '@vzeta/camunda-api-zod-schemas/8.8';
import {useRemoveFormReference} from 'v1/api/useTask.query';
import {DetailsFooter} from 'common/tasks/details/DetailsFooter';
import {type InlineLoadingProps, Layer} from '@carbon/react';
import {notificationsStore} from 'common/notifications/notifications.store';
import {FormManager} from 'common/form-js/formManager';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'common/tasks/details/TaskDetailsLayout';
import {useForm} from 'v1/api/useForm.query';
import {useVariables} from 'v1/api/useVariables.query';
import {FormJSRenderer} from 'common/form-js/FormJSRenderer';
import {FailedVariableFetchError} from 'common/tasks/details/FailedVariableFetchError';
import {Pattern, match} from 'ts-pattern';
import {CompleteTaskButton} from 'common/tasks/details/CompleteTaskButton';
import {useTranslation} from 'react-i18next';
import {extractVariablesFromFormSchema} from 'common/tasks/details/extractVariablesFromFormSchema';
import {formatVariablesToFormData} from 'common/tasks/details/formatVariablesToFormData';
import {ActiveTransitionLoadingText} from 'common/tasks/details/ActiveTransitionLoadingText';

type Props = {
  id: Form['id'];
  processDefinitionKey: Form['processDefinitionKey'];
  task: Task;
  onSubmit: React.ComponentProps<typeof FormJSRenderer>['handleSubmit'];
  onFileUpload: React.ComponentProps<typeof FormJSRenderer>['handleFileUpload'];
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
  onFileUpload,
  onSubmitFailure,
  user,
}) => {
  const {t} = useTranslation();
  const formManagerRef = useRef<FormManager | null>(null);
  const {assignee, taskState, formVersion} = task;
  const [submissionState, setSubmissionState] = useState<
    NonNullable<InlineLoadingProps['status']>
  >(() => (taskState === 'COMPLETING' ? 'active' : 'inactive'));
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
    user.username === assignee &&
    taskState === 'CREATED' &&
    hasFetchedVariables;
  const {removeFormReference} = useRemoveFormReference(task);

  const shouldHideBottomPanel =
    taskState === 'ASSIGNING' ||
    (taskState === 'UPDATING' && assignee === null) ||
    (taskState === 'CANCELING' && assignee === null);

  return (
    <ScrollableContent data-testid="embedded-form" tabIndex={-1}>
      <TaskDetailsContainer>
        <Layer as={TaskDetailsRow}>
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
                  handleFileUpload={onFileUpload}
                  onImportError={() => {
                    removeFormReference();
                    notificationsStore.displayNotification({
                      kind: 'error',
                      title: t('formJSInvalidSchemaErrorNotificationTitle'),
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
        </Layer>
        {!shouldHideBottomPanel && (
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
                if (taskState === 'COMPLETING') {
                  setSubmissionState('active');
                } else {
                  setSubmissionState('inactive');
                }
              }}
              isHidden={['COMPLETED', 'CANCELING', 'UPDATING'].includes(
                taskState,
              )}
              isDisabled={!canCompleteTask}
            />
            {['UPDATING', 'CANCELING'].includes(taskState) && (
              <ActiveTransitionLoadingText taskState={taskState} />
            )}
          </DetailsFooter>
        )}
      </TaskDetailsContainer>
    </ScrollableContent>
  );
};

export {FormJS};
