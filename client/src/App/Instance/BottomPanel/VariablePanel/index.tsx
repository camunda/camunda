/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import Variables from '../Variables';
import {variablesStore} from 'modules/stores/variables';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {useInstancePageParams} from 'App/Instance/useInstancePageParams';
import {Form} from 'react-final-form';
import {useNotifications} from 'modules/notifications';

import * as Styled from './styled';

type FormValues = {
  name?: string;
  value?: string;
};

const VariablePanel = observer(function VariablePanel() {
  const {processInstanceId = ''} = useInstancePageParams();
  const notifications = useNotifications();

  useEffect(() => {
    variablesStore.init(processInstanceId);

    return () => {
      variablesStore.reset();
    };
  }, [processInstanceId]);

  const {displayStatus} = variablesStore;

  return (
    <Styled.VariablesPanel>
      {displayStatus === 'error' ? (
        <StatusMessage variant="error">
          Variables could not be fetched
        </StatusMessage>
      ) : displayStatus === 'multi-instances' ? (
        <StatusMessage variant="default">
          To view the Variables, select a single Flow Node Instance in the
          Instance History.
        </StatusMessage>
      ) : (
        <Form<FormValues>
          onSubmit={async (values, form) => {
            const {initialValues} = form.getState();

            const {name, value} = values;

            if (name === undefined || value === undefined) {
              return;
            }

            const params = {
              id: processInstanceId,
              name,
              value,
              onSuccess: () => {
                notifications.displayNotification('success', {
                  headline: 'Variable added',
                });
                form.reset({});
              },
              onError: () => {
                notifications.displayNotification('error', {
                  headline: 'Variable could not be saved',
                });
                form.reset({});
              },
            };

            if (initialValues.name === '') {
              const result = await variablesStore.addVariable(params);
              if (result === 'VALIDATION_ERROR') {
                return {name: 'Variable should be unique'};
              }
            } else if (initialValues.name === name) {
              variablesStore.updateVariable(params);
              form.reset({});
            }
          }}
        >
          {({handleSubmit}) => {
            return (
              <form onSubmit={handleSubmit}>
                <Variables />
              </form>
            );
          }}
        </Form>
      )}
    </Styled.VariablesPanel>
  );
});

export {VariablePanel};
