/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useLayoutEffect, useRef, useState} from 'react';
import {useQuery} from '@apollo/client';
import {GET_FORM, GetForm, FormQueryVariables} from 'modules/queries/get-form';
import {Form, Variable} from 'modules/types';
import {GetTask, useRemoveFormReference} from 'modules/queries/get-task';
import {
  GetCurrentUser,
  GET_CURRENT_USER,
} from 'modules/queries/get-current-user';
import {createForm} from '@bpmn-io/form-js';
import '@bpmn-io/form-js/dist/assets/form-js.css';
import {DetailsFooter} from 'modules/components/DetailsFooter';
import {Button} from 'modules/components/Button';
import {Container, FormContainer, FormCustomStyling} from './styled';
import {PanelTitle} from 'modules/components/PanelTitle';
import {PanelHeader} from 'modules/components/PanelHeader';
import {useSelectedVariables} from 'modules/queries/get-selected-variables';
import {useNotifications} from 'modules/notifications';

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
    const parsedSchema = JSON.parse(schema);

    return Array.isArray(parsedSchema.components)
      ? parsedSchema.components
          .filter(({type}: any) => type === 'textfield')
          .map(({key}: any) => key)
      : [];
  } catch {
    return [];
  }
}

type Props = {
  id: Form['id'];
  processDefinitionId: Form['processDefinitionId'];
  task: GetTask['task'];
  onSubmit: (variables: Variable[]) => Promise<void>;
};

const FormJS: React.FC<Props> = ({id, processDefinitionId, task, onSubmit}) => {
  const {data} = useQuery<GetForm, FormQueryVariables>(GET_FORM, {
    variables: {
      id,
      processDefinitionId,
    },
  });
  const {data: userData} = useQuery<GetCurrentUser>(GET_CURRENT_USER);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const formRef = useRef<ReturnType<typeof createForm> | null>(null);
  const {assignee, taskState} = task;
  const {
    form: {schema},
  } = data ?? {
    form: {
      schema: null,
    },
  };
  const {
    variables,
    loading: areVariablesLoading,
    updateSelectedVariables,
  } = useSelectedVariables(task.id, extractVariablesFromFormSchema(schema));
  const [isFormValid, setIsFormValid] = useState(false);
  const canCompleteTask =
    userData?.currentUser.username === assignee?.username &&
    taskState === 'CREATED';
  const {removeFormReference} = useRemoveFormReference(task);
  const {displayNotification} = useNotifications();

  useEffect(() => {
    const container = containerRef.current;

    if (
      container !== null &&
      schema !== null &&
      formRef.current === null &&
      !areVariablesLoading
    ) {
      const data = formatVariablesToFormData(variables);
      try {
        const form = createForm({
          schema: JSON.parse(schema),
          data,
          container,
          properties: {
            readOnly: !canCompleteTask,
          },
        });

        form.on('changed', ({errors}: any) => {
          setIsFormValid(Object.keys(errors).length === 0);
        });

        form.on('submit', async ({errors, data}: any) => {
          if (Object.keys(errors).length === 0) {
            const variables = Object.entries(data).map(
              ([name, value]) =>
                ({
                  name,
                  value: JSON.stringify(value),
                } as Variable),
            );
            await onSubmit(variables);
            updateSelectedVariables(variables);
          }
        });

        setIsFormValid(Object.keys(form.validateAll(data)).length === 0);
        formRef.current = form;
      } catch {
        removeFormReference();
        displayNotification('error', {
          headline: 'Invalid Form schema',
        });
      }
    }
  }, [
    canCompleteTask,
    onSubmit,
    schema,
    variables,
    areVariablesLoading,
    updateSelectedVariables,
    removeFormReference,
    displayNotification,
  ]);

  useLayoutEffect(() => {
    formRef.current?.setProperty('readOnly', !canCompleteTask);
    formRef.current?.reset();
  }, [canCompleteTask]);

  useEffect(() => {
    if (taskState === 'COMPLETED') {
      formRef.current?.setState({
        data: formatVariablesToFormData(variables),
      });
    }
  }, [taskState, variables]);

  useEffect(() => {
    return () => {
      formRef.current = null;
      setIsFormValid(false);
    };
  }, [task.id]);

  return (
    <Container hasFooter={canCompleteTask} data-testid="embedded-form">
      <PanelHeader>
        <PanelTitle>Task Form</PanelTitle>
      </PanelHeader>
      <FormCustomStyling />
      <FormContainer ref={containerRef} key={task.id} />
      {canCompleteTask && (
        <DetailsFooter>
          <Button
            type="submit"
            disabled={!isFormValid}
            onClick={() => formRef.current?.submit()}
          >
            Complete Task
          </Button>
        </DetailsFooter>
      )}
    </Container>
  );
};

export {FormJS};
