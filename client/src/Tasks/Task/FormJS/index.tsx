/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
import {Form, Variable, CurrentUser, Task} from 'modules/types';
import {useRemoveFormReference} from 'modules/queries/useTask';
import {getSchemaVariables} from '@bpmn-io/form-js-viewer';
import '@bpmn-io/form-js-viewer/dist/assets/form-js-base.css';
import {DetailsFooter} from 'modules/components/DetailsFooter';
import {InlineLoadingStatus} from '@carbon/react';
import {FormCustomStyling} from './styled';
import {usePermissions} from 'modules/hooks/usePermissions';
import {notificationsStore} from 'modules/stores/notifications';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {getCompletionButtonDescription} from 'modules/utils/getCompletionButtonDescription';
import {formManager} from 'modules/formManager';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'modules/components/TaskDetailsLayout';
import {Separator} from 'modules/components/Separator';
import {useForm} from 'modules/queries/useForm';
import {useVariables} from 'modules/queries/useVariables';

function formatVariablesToFormData(variables: Variable[]) {
  return variables.reduce(
    (accumulator, {name, value}) => ({
      ...accumulator,
      [name]: JSON.parse(value),
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
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [submissionState, setSubmissionState] =
    useState<InlineLoadingStatus>('inactive');
  const {assignee, taskState} = task;
  const {data, isInitialLoading} = useForm(
    {
      id,
      processDefinitionKey,
    },
    {
      refetchOnReconnect: false,
      refetchOnWindowFocus: false,
    },
  );
  const {hasPermission} = usePermissions(['write']);
  const {schema} = data;
  const extractedVariables = extractVariablesFromFormSchema(schema);
  const {isFetching, data: variablesData} = useVariables(
    {
      taskId: task.id,
      variableNames: extractedVariables,
    },
    {
      enabled: !isInitialLoading && extractedVariables.length > 0,
      refetchOnReconnect: assignee === null,
      refetchOnWindowFocus: assignee === null,
    },
  );
  const variables = useMemo(() => variablesData ?? [], [variablesData]);
  const isAssignedToMe = user.userId === assignee;
  const canCompleteTask =
    user.userId === assignee && taskState === 'CREATED' && hasPermission;
  const {removeFormReference} = useRemoveFormReference(task);

  useEffect(() => {
    formManager.setReadOnly(!canCompleteTask);

    if (!isAssignedToMe) {
      formManager.reset();
    }
  }, [canCompleteTask, isAssignedToMe]);

  useEffect(() => {
    const container = containerRef.current;

    function onImportError() {
      removeFormReference();
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Invalid Form schema',
        isDismissable: true,
      });
    }

    if (
      schema !== null &&
      !isFetching &&
      container !== null &&
      submissionState === 'inactive'
    ) {
      const data = formatVariablesToFormData(variables);
      formManager.render({
        container,
        data,
        schema,
        onImportError,
        onSubmit: async ({errors, data}: any) => {
          if (Object.keys(errors).length === 0) {
            const variables = Object.entries(data).map(
              ([name, value]) =>
                ({
                  name,
                  value: JSON.stringify(value),
                } as Variable),
            );
            try {
              setSubmissionState('active');
              await onSubmit(variables);
              setSubmissionState('finished');
            } catch (error) {
              onSubmitFailure(error as Error);
              setSubmissionState('error');
            }
          } else {
            setSubmissionState('inactive');
          }
        },
      });
    }
  }, [
    isFetching,
    variables,
    schema,
    removeFormReference,
    onSubmit,
    onSubmitFailure,
    submissionState,
  ]);

  useEffect(() => {
    return () => {
      formManager.detach();
    };
  }, [task.id]);

  return (
    <>
      <Separator />
      <ScrollableContent data-testid="embedded-form" tabIndex={-1}>
        <TaskDetailsContainer>
          <FormCustomStyling />
          <TaskDetailsRow ref={containerRef} />
          <DetailsFooter>
            <AsyncActionButton
              inlineLoadingProps={{
                description: getCompletionButtonDescription(submissionState),
                'aria-live': ['error', 'finished'].includes(submissionState)
                  ? 'assertive'
                  : 'polite',
                onSuccess: () => {
                  onSubmitSuccess();
                  setSubmissionState('inactive');
                },
              }}
              buttonProps={{
                className: taskState === 'COMPLETED' ? 'hide' : '',
                size: 'md',
                type: 'submit',
                disabled: submissionState === 'active' || !canCompleteTask,
                onClick: () => {
                  setSubmissionState('active');
                  formManager.submit();
                },
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
      </ScrollableContent>
    </>
  );
};

export {FormJS};
