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
