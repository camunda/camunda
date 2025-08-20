/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form as ReactFinalForm} from 'react-final-form';
import {type VariableFormValues} from 'modules/types/variables';

import arrayMutators from 'final-form-arrays';
import {VariablesForm} from './VariablesForm';
import {notificationsStore} from 'modules/stores/notifications';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useQueryClient} from '@tanstack/react-query';
import {addVariable, updateVariable} from 'modules/utils/variables';
import {queryKeys} from 'modules/queries/queryKeys';

type Props = {
  scopeId: string;
};

const VariablesFinalForm: React.FC<Props> = ({scopeId}) => {
  const queryClient = useQueryClient();
  const {processInstanceId = ''} = useProcessInstancePageParams();

  return (
    <ReactFinalForm<VariableFormValues>
      mutators={{
        ...arrayMutators,
        triggerValidation(fieldsToValidate: string[], state, {changeValue}) {
          fieldsToValidate.forEach((fieldName) => {
            changeValue(state, fieldName, (n) => n);
          });
        },
      }}
      key={scopeId}
      render={(props) => <VariablesForm {...props} />}
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
          invalidateQueries: () => {
            queryClient.invalidateQueries({
              queryKey: queryKeys.variables.search(),
            });
          },
          onSuccess: () => {
            notificationsStore.displayNotification({
              kind: 'success',
              title: 'Variable added',
              isDismissable: true,
            });

            form.reset({});
          },
          onError: (statusCode: number) => {
            notificationsStore.displayNotification({
              kind: 'error',
              title: 'Variable could not be saved',
              subtitle:
                statusCode === 403 ? 'You do not have permission' : undefined,
              isDismissable: true,
            });

            form.reset({});
          },
        };

        if (initialValues.name === '') {
          const result = await addVariable(params);
          if (result === 'VALIDATION_ERROR') {
            return {name: 'Name should be unique'};
          }
        } else if (initialValues.name === name) {
          updateVariable(params);
          form.reset({});
        }
      }}
    />
  );
};

export {VariablesFinalForm};
