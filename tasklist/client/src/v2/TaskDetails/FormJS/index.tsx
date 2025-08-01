/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Layer, type InlineLoadingProps} from '@carbon/react';
import type {CurrentUser, UserTask} from '@vzeta/camunda-api-zod-schemas/8.8';
import {FormJSRenderer} from 'common/form-js/FormJSRenderer';
import type {FormManager} from 'common/form-js/formManager';
import {notificationsStore} from 'common/notifications/notifications.store';
import {CompleteTaskButton} from 'common/tasks/details/CompleteTaskButton';
import {DetailsFooter} from 'common/tasks/details/DetailsFooter';
import {extractVariablesFromFormSchema} from 'common/tasks/details/extractVariablesFromFormSchema';
import {FailedVariableFetchError} from 'common/tasks/details/FailedVariableFetchError';
import {formatVariablesToFormData} from 'common/tasks/details/formatVariablesToFormData';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'common/tasks/details/TaskDetailsLayout';
import {ActiveTransitionLoadingText} from 'common/tasks/details/ActiveTransitionLoadingText';
import {useMemo, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {match, Pattern} from 'ts-pattern';
import {useSelectedVariables} from 'v2/api/useSelectedVariables.query';
import {useRemoveFormReference} from 'v2/api/useTask.query';
import {useUserTaskForm} from 'v2/api/useUserTaskForm.query';
import {tryParseJSON} from 'v2/features/tasks/details/tryParseJSON';

type Props = {
  task: UserTask;
  onSubmit: (variables: Record<string, unknown>) => Promise<void>;
  onFileUpload: React.ComponentProps<typeof FormJSRenderer>['handleFileUpload'];
  onSubmitSuccess: () => void;
  onSubmitFailure: (error: Error) => void;
  user: CurrentUser;
};

const FormJS: React.FC<Props> = ({
  task,
  onSubmit,
  onFileUpload,
  onSubmitFailure,
  onSubmitSuccess,
  user,
}) => {
  const {t} = useTranslation();
  const formManagerRef = useRef<FormManager | null>(null);
  const {userTaskKey, state, assignee} = task;
  const [localSubmissionState, setLocalSubmissionState] = useState<
    NonNullable<InlineLoadingProps['status']>
  >(() => (state === 'COMPLETING' ? 'active' : 'inactive'));

  const submissionState =
    state === 'COMPLETING' ? 'active' : localSubmissionState;

  const {data, isLoading} = useUserTaskForm(
    {userTaskKey},
    {
      refetchOnReconnect: false,
      refetchOnWindowFocus: false,
    },
  );
  const {schema} = data ?? {};
  const extractedVariables = extractVariablesFromFormSchema(schema);
  const {data: variablesData, status} = useSelectedVariables(
    {
      userTaskKey,
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
    user.username === assignee && state === 'CREATED' && hasFetchedVariables;

  const {removeFormReference} = useRemoveFormReference(task);
  return (
    <ScrollableContent data-testid="embedded-form" tabIndex={-1}>
      <TaskDetailsContainer>
        <Layer as={TaskDetailsRow}>
          {match({schema, status})
            .with(
              {
                schema: undefined,
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
                schema: Pattern.not(undefined),
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
                  handleSubmit={(variables) => {
                    return onSubmit(
                      variables.reduce(
                        (acc, {name, value}) => ({
                          ...acc,
                          [name]: tryParseJSON(value),
                        }),
                        {},
                      ),
                    );
                  }}
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
                    setLocalSubmissionState('active');
                  }}
                  onSubmitSuccess={() => {
                    setLocalSubmissionState('finished');
                  }}
                  onSubmitError={(error) => {
                    onSubmitFailure(error as Error);
                    setLocalSubmissionState('error');
                  }}
                  onValidationError={() => {
                    if (state !== 'COMPLETING') {
                      setLocalSubmissionState('inactive');
                    }
                  }}
                />
              ),
            )
            .otherwise(() => null)}
        </Layer>
        <DetailsFooter>
          <CompleteTaskButton
            submissionState={submissionState}
            onClick={() => {
              setLocalSubmissionState('active');
              formManagerRef.current?.submit();
            }}
            onSuccess={() => {
              onSubmitSuccess();
              setLocalSubmissionState('inactive');
            }}
            onError={() => {
              if (state === 'COMPLETING') {
                setLocalSubmissionState('active');
              } else {
                setLocalSubmissionState('inactive');
              }
            }}
            isHidden={state === 'COMPLETED'}
            isDisabled={!canCompleteTask}
          />
        </DetailsFooter>
      </TaskDetailsContainer>
    </ScrollableContent>
  );
};

export {FormJS};
