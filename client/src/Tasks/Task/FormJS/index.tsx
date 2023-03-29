/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {useQuery} from '@apollo/client';
import {GET_FORM, GetForm, FormQueryVariables} from 'modules/queries/get-form';
import {Form, Variable, User} from 'modules/types';
import {GetTask, useRemoveFormReference} from 'modules/queries/get-task';
import {getSchemaVariables} from '@bpmn-io/form-js-viewer';
import '@bpmn-io/form-js-viewer/dist/assets/form-js.css';
import {DetailsFooter} from 'modules/components/DetailsFooter';
import {InlineLoadingStatus} from '@carbon/react';
import {FormCustomStyling} from './styled';
import {useSelectedVariables} from 'modules/queries/get-selected-variables';
import {usePermissions} from 'modules/hooks/usePermissions';
import {notificationsStore} from 'modules/stores/notifications';
import {AsyncActionButton} from 'modules/components/AsyncActionButton';
import {getCompletionButtonDescription} from 'modules/utils/getCompletionButtonDescription';
import {formManager} from './formManager';
import {
  ScrollableContent,
  TaskDetailsContainer,
  TaskDetailsRow,
} from 'modules/components/TaskDetailsLayout';
import {Separator} from 'modules/components/Separator';

function formatVariablesToFormData(variables: ReadonlyArray<Variable>) {
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
  processDefinitionId: Form['processDefinitionId'];
  task: GetTask['task'];
  onSubmit: (variables: Variable[]) => Promise<void>;
  onSubmitSuccess: () => void;
  onSubmitFailure: (error: Error) => void;
  user: User;
};

const FormJS: React.FC<Props> = ({
  id,
  processDefinitionId,
  task,
  onSubmit,
  onSubmitSuccess,
  onSubmitFailure,
  user,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [submissionState, setSubmissionState] =
    useState<InlineLoadingStatus>('inactive');
  const {data} = useQuery<GetForm, FormQueryVariables>(GET_FORM, {
    variables: {
      id,
      processDefinitionId,
    },
  });
  const {hasPermission} = usePermissions(['write']);
  const {assignee, taskState} = task;
  const {
    form: {schema},
  } = data ?? {
    form: {
      schema: null,
    },
  };
  const {updateSelectedVariables, loading, variables} = useSelectedVariables(
    task.id,
    extractVariablesFromFormSchema(schema),
  );
  const isClaimed = user.userId === assignee;
  const canCompleteTask =
    user.userId === assignee && taskState === 'CREATED' && hasPermission;
  const {removeFormReference} = useRemoveFormReference(task);

  useEffect(() => {
    formManager.setReadOnly(!canCompleteTask);

    if (!isClaimed) {
      formManager.reset();
    }
  }, [canCompleteTask, isClaimed]);

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

    function parseSchema(schema: string) {
      try {
        return JSON.parse(schema);
      } catch {
        onImportError();
      }
    }

    if (
      schema !== null &&
      !loading &&
      container !== null &&
      submissionState === 'inactive'
    ) {
      const data = formatVariablesToFormData(variables);
      formManager.render({
        container,
        data,
        schema: parseSchema(schema),
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
              updateSelectedVariables(variables);
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
    loading,
    variables,
    schema,
    removeFormReference,
    updateSelectedVariables,
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
      <ScrollableContent data-testid="embedded-form">
        <TaskDetailsContainer>
          <FormCustomStyling />
          <TaskDetailsRow ref={containerRef} tabIndex={-1} />
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
                size: 'md',
                type: 'submit',
                disabled: submissionState === 'active' || !canCompleteTask,
                onClick: () => {
                  setSubmissionState('active');
                  formManager.submit();
                },
                title: canCompleteTask
                  ? undefined
                  : 'You must first claim this task to complete it',
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
