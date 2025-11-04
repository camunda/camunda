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
import {queryKeys} from 'modules/queries/queryKeys';
import {useElementInstanceVariables} from 'modules/mutations/elementInstances/useElementInstanceVariables';

type Props = {
  scopeId: string;
};

const VariablesFinalForm: React.FC<Props> = ({scopeId}) => {
  const queryClient = useQueryClient();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {mutateAsync: mutateAsyncVariables} = useElementInstanceVariables(
    scopeId,
    processInstanceId,
  );

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
        const isNewVariable = initialValues?.name === '';
        const {name, value} = values;

        try {
          await mutateAsyncVariables({
            name,
            value: JSON.stringify(JSON.parse(value)),
          });

          notificationsStore.displayNotification({
            kind: 'success',
            title: isNewVariable ? 'Variable added' : 'Variable updated',
            isDismissable: true,
          });
        } catch (error) {
          if (error instanceof Error) {
            notificationsStore.displayNotification({
              kind: 'error',
              title: 'Variable could not be saved',
              subtitle: error.message,
              isDismissable: true,
            });
          }
        } finally {
          form.reset({});
          await queryClient.invalidateQueries({
            queryKey: queryKeys.variables.search(),
          });
        }
      }}
    />
  );
};

export {VariablesFinalForm};
