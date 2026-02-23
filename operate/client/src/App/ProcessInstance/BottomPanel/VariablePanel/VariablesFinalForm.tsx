/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form as ReactFinalForm} from 'react-final-form';
import {useMemo} from 'react';
import {type VariableFormValues} from 'modules/types/variables';

import arrayMutators from 'final-form-arrays';
import {VariablesForm} from './VariablesForm';
import {notificationsStore} from 'modules/stores/notifications';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useQueryClient} from '@tanstack/react-query';
import {queryKeys} from 'modules/queries/queryKeys';
import {useElementInstanceVariables} from 'modules/mutations/elementInstances/useElementInstanceVariables';
import {handleMutationError} from 'modules/utils/notifications';
import {modificationsStore} from 'modules/stores/modifications';

type Props = {
  scopeKey: string;
};

const VariablesFinalForm: React.FC<Props> = ({scopeKey}) => {
  const queryClient = useQueryClient();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {mutateAsync: mutateAsyncVariables} = useElementInstanceVariables(
    scopeKey,
    processInstanceId,
  );

  const initialValues = useMemo(() => {
    if (!modificationsStore.isModificationModeEnabled) {
      return {};
    }
    const addVariableModifications =
      modificationsStore.getAddVariableModifications(scopeKey);
    if (addVariableModifications.length === 0) {
      return {};
    }
    return {
      newVariables: addVariableModifications,
    };
  }, [scopeKey]);

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
      key={scopeKey}
      initialValues={initialValues}
      render={(props) => <VariablesForm {...props} />}
      onSubmit={async (values, form) => {
        const {initialValues} = form.getState();
        const isNewVariable = initialValues?.name === '';
        const {name, value} = values;

        await mutateAsyncVariables(
          {name, value: JSON.stringify(JSON.parse(value))},
          {
            onSuccess: () => {
              notificationsStore.displayNotification({
                kind: 'success',
                title: isNewVariable ? 'Variable added' : 'Variable updated',
                isDismissable: true,
              });
            },
            onError: (error) => {
              handleMutationError({
                statusCode: error.status,
                title: 'Variable could not be saved',
                subtitle: error.statusText,
              });
            },
            onSettled: async () => {
              form.reset({});
              await queryClient.invalidateQueries({
                queryKey: queryKeys.variables.search(),
              });
            },
          },
        ).catch(() => void 0);
      }}
    />
  );
};

export {VariablesFinalForm};
