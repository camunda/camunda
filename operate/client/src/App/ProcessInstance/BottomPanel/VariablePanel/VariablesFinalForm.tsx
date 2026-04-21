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
import {modificationsStore} from 'modules/stores/modifications';

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

  const initialValues = useMemo(() => {
    if (!modificationsStore.isModificationModeEnabled) {
      return {};
    }
    const addVariableModifications =
      modificationsStore.getAddVariableModifications(scopeId);
    if (addVariableModifications.length === 0) {
      return {};
    }
    return {
      newVariables: addVariableModifications,
    };
  }, [scopeId]);

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
      initialValues={initialValues}
      render={(props) => <VariablesForm {...props} />}
      onSubmit={async (values, form) => {
        const {initialValues} = form.getState();
        const isNewVariable = initialValues?.name === '';
        const {name, value} = values;
        const variableKey = initialValues?.variableKey;

<<<<<<< HEAD:operate/client/src/App/ProcessInstance/BottomPanel/VariablePanel/VariablesFinalForm.tsx
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
=======
        await mutateAsyncVariables(
          {name, value: JSON.stringify(JSON.parse(value)), variableKey},
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
>>>>>>> dbca852b (fix: truncated variables create/update stuck in loading state):operate/client/src/App/ProcessInstance/BottomPanelTabs/VariablesTab/VariablesFinalForm.tsx
      }}
    />
  );
};

export {VariablesFinalForm};
