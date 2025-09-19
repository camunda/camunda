/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {variablesStore} from 'modules/stores/variables';
import {VariablesContent, EmptyMessageWrapper} from './styled';
import {observer} from 'mobx-react';
import {reaction} from 'mobx';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useForm, useFormState} from 'react-final-form';
import {Restricted} from 'modules/components/Restricted';
import {modificationsStore} from 'modules/stores/modifications';
import {useFieldArray} from 'react-final-form-arrays';
import {type VariableFormValues} from 'modules/types/variables';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {VariablesTable} from './VariablesTable';
import {Footer} from './Footer';
import {Skeleton} from './Skeleton';
import {useNewScopeIdForFlowNode} from 'modules/hooks/modifications';
import {usePermissions} from 'modules/queries/permissions/usePermissions';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {useIsProcessInstanceRunning} from 'modules/queries/processInstance/useIsProcessInstanceRunning';
import {useIsRootNodeSelected} from 'modules/hooks/flowNodeSelection';
import {getScopeId} from 'modules/utils/variables';
import {useVariables} from 'modules/queries/variables/useVariables';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';

type Props = {
  isVariableModificationAllowed?: boolean;
};

type FooterVariant = React.ComponentProps<typeof Footer>['variant'];

const Variables: React.FC<Props> = observer(
  ({isVariableModificationAllowed = false}) => {
    const {displayStatus} = useVariables();
    const newScopeIdForFlowNode = useNewScopeIdForFlowNode(
      flowNodeSelectionStore.state.selection?.flowNodeId,
    );
    const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();
    const {data: permissions} = usePermissions();
    const isRootNodeSelected = useIsRootNodeSelected();
    const {
      state: {pendingItem},
    } = variablesStore;
    const [footerVariant, setFooterVariant] =
      useState<FooterVariant>('initial');

    const processDefinitionKey = useProcessDefinitionKeyContext();
    const {
      error: processDefinitionError,
      isLoading: isProcessDefinitionLoading,
    } = useProcessInstanceXml({
      processDefinitionKey,
    });

    const scopeId = getScopeId() ?? newScopeIdForFlowNode;

    const {isModificationModeEnabled} = modificationsStore;

    const form = useForm<VariableFormValues>();

    useEffect(() => {
      const disposer = reaction(
        () => modificationsStore.isModificationModeEnabled,
        (isModificationModeEnabled) => {
          if (!isModificationModeEnabled) {
            form.reset({});
          }
        },
      );

      return disposer;
    }, [isModificationModeEnabled, form]);

    const {initialValues} = useFormState();

    const fieldArray = useFieldArray('newVariables');

    const isViewMode = isModificationModeEnabled
      ? fieldArray.fields.length === 0 &&
        modificationsStore.getAddVariableModifications(scopeId).length === 0
      : initialValues === undefined ||
        Object.values(initialValues).length === 0;

    useEffect(() => {
      const isSelectedInstanceRunning =
        flowNodeMetaDataStore.isSelectedInstanceRunning;

      if (
        isProcessDefinitionLoading ||
        processDefinitionError?.response?.status === HTTP_STATUS_FORBIDDEN
      ) {
        setFooterVariant('hidden');
        return;
      }

      if (!isProcessInstanceRunning) {
        setFooterVariant('disabled');
        return;
      }

      if (pendingItem !== null) {
        setFooterVariant('pending-variable');
        return;
      }

      if (initialValues?.name === '' && initialValues?.value === '') {
        setFooterVariant('add-variable');
        return;
      }

      if (!isViewMode || (!isRootNodeSelected && !isSelectedInstanceRunning)) {
        setFooterVariant('disabled');
        return;
      }

      setFooterVariant('initial');
    }, [
      isProcessInstanceRunning,
      pendingItem,
      initialValues,
      isViewMode,
      isRootNodeSelected,
      isProcessDefinitionLoading,
      processDefinitionError,
    ]);

    if (displayStatus === 'no-content') {
      return null;
    }

    return (
      <VariablesContent>
        {isViewMode && displayStatus === 'skeleton' && <Skeleton />}
        {isViewMode && displayStatus === 'no-variables' && (
          <EmptyMessageWrapper>
            <EmptyMessage message="The Flow Node has no Variables" />
          </EmptyMessageWrapper>
        )}
        {(!isViewMode || displayStatus === 'variables') && (
          <VariablesTable
            scopeId={scopeId}
            isVariableModificationAllowed={isVariableModificationAllowed}
          />
        )}

        {!isModificationModeEnabled && (
          <Restricted
            resourceBasedRestrictions={{
              scopes: ['UPDATE_PROCESS_INSTANCE'],
              permissions: permissions,
            }}
          >
            <Footer variant={footerVariant} />
          </Restricted>
        )}
      </VariablesContent>
    );
  },
);

export default Variables;
