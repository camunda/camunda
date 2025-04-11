/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {VariablesContent, EmptyMessageWrapper} from '../styled';
import {observer} from 'mobx-react';
import {computed, reaction} from 'mobx';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useForm, useFormState} from 'react-final-form';
import {Restricted} from 'modules/components/Restricted';
import {modificationsStore} from 'modules/stores/modifications';
import {useFieldArray} from 'react-final-form-arrays';
import {VariableFormValues} from 'modules/types/variables';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {VariablesTable} from '../VariablesTable';
import {Footer} from '../Footer';
import {Skeleton} from '../Skeleton';
import {useDisplayStatus} from 'modules/hooks/variables';

type Props = {
  isVariableModificationAllowed?: boolean;
};

const Variables: React.FC<Props> = observer(
  ({isVariableModificationAllowed = false}) => {
    const displayStatus = useDisplayStatus();
    const {
      state: {pendingItem, loadingItemId, status},
    } = variablesStore;

    const scopeId =
      variablesStore.scopeId ??
      modificationsStore.getNewScopeIdForFlowNode(
        flowNodeSelectionStore.state.selection?.flowNodeId,
      );

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

    const footerVariant = computed(() => {
      if (!processInstanceDetailsStore.isRunning) {
        return 'disabled';
      }

      if (pendingItem !== null) {
        return 'pending-variable';
      }

      if (initialValues?.name === '' && initialValues?.value === '') {
        return 'add-variable';
      }

      if (
        status === 'first-fetch' ||
        !isViewMode ||
        (!flowNodeSelectionStore.isRootNodeSelected &&
          !flowNodeMetaDataStore.isSelectedInstanceRunning) ||
        loadingItemId !== null
      ) {
        return 'disabled';
      }

      return 'initial';
    });

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
              permissions: processInstanceDetailsStore.getPermissions(),
            }}
          >
            <Footer variant={footerVariant.get()} />
          </Restricted>
        )}
      </VariablesContent>
    );
  },
);

export default Variables;
