/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Form as ReactFinalForm, useForm} from 'react-final-form';
import {type VariableFormValues} from 'modules/types/variables';

import {Content, EmptyMessageContainer} from './styled';
import arrayMutators from 'final-form-arrays';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {Loading} from '@carbon/react';
import {VariablesForm} from './VariablesForm';
import {notificationsStore} from 'modules/stores/notifications';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {getScopeId} from 'modules/utils/variables';
import {useQueryClient} from '@tanstack/react-query';
import {
  VARIABLES_SEARCH_QUERY_KEY,
  useVariables,
} from 'modules/queries/variables/useVariables';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useElementInstanceVariables} from 'modules/mutations/elementInstances/useElementInstanceVariables';
import {variablesStore} from 'modules/stores/variables';

const VariablesContent: React.FC = observer(() => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const elementInstanceKey = flowNodeSelectionStore.selectedFlowNodeInstanceId;
  const {displayStatus} = useVariables();
  const queryClient = useQueryClient();

  // todo: remove ! operator
  const mutation = useElementInstanceVariables(
    elementInstanceKey!,
    processInstanceId,
  );

  if (displayStatus === 'error') {
    return (
      <EmptyMessageContainer>
        <ErrorMessage message="Variables could not be fetched" />
      </EmptyMessageContainer>
    );
  }

  if (displayStatus === 'multi-instances') {
    return (
      <EmptyMessageContainer>
        <EmptyMessage
          message="To view the Variables, select a single Flow Node Instance in the
          Instance History."
        />
      </EmptyMessageContainer>
    );
  }

  return (
    <Content>
      {displayStatus === 'spinner' && (
        <Loading data-testid="variables-spinner" />
      )}
      <ReactFinalForm<VariableFormValues>
        mutators={{
          ...arrayMutators,
          triggerValidation(fieldsToValidate: string[], state, {changeValue}) {
            fieldsToValidate.forEach((fieldName) => {
              changeValue(state, fieldName, (n) => n);
            });
          },
        }}
        key={getScopeId()}
        render={(props) => <VariablesForm {...props} />}
        onSubmit={async (values, form) => {
          const {initialValues} = form.getState();
          const isNewVariable = initialValues.name === '';

          const {name, value} = values;

          if (!elementInstanceKey) {
            return;
          }

          if (isNewVariable) {
            variablesStore.setPendingItem({
              name,
              value,
              hasActiveOperation: true,
              isFirst: false,
              sortValues: null,
              isPreview: false,
            });
          }

          mutation.mutate(
            {name, value},
            {
              onSuccess: () => {
                notificationsStore.displayNotification({
                  kind: 'success',
                  title: isNewVariable ? 'Variable added' : 'Variable updated',
                  isDismissable: true,
                });
              },
              onError: () => {
                notificationsStore.displayNotification({
                  kind: 'error',
                  title: 'Variable could not be saved',
                  // subtitle:
                  //   error.statusCode === 403
                  //     ? 'You do not have permission'
                  //     : undefined,
                  isDismissable: true,
                });
              },
              onSettled: async () => {
                await queryClient.invalidateQueries({
                  queryKey: [VARIABLES_SEARCH_QUERY_KEY],
                });
                if (isNewVariable) {
                  variablesStore.setPendingItem(null);
                }
                form.reset({});
              },
            },
          );
        }}
      />
    </Content>
  );
});

export {VariablesContent};
