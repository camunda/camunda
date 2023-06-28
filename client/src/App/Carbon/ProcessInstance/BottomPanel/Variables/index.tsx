/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {VariablesContent, EmptyMessageWrapper, Footer} from './styled';
import {observer} from 'mobx-react';
import {reaction} from 'mobx';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useForm, useFormState} from 'react-final-form';
import {Restricted} from 'modules/components/Restricted';
import {modificationsStore} from 'modules/stores/modifications';
import {AddVariableButton} from './AddVariableButton';
import {useFieldArray} from 'react-final-form-arrays';
import {VariableFormValues} from 'modules/types/variables';
import {EmptyMessage} from 'modules/components/Carbon/EmptyMessage';
import {VariablesTable} from './VariablesTable';

type Props = {
  isVariableModificationAllowed?: boolean;
};

const Variables: React.FC<Props> = observer(
  ({isVariableModificationAllowed = false}) => {
    const {
      state: {pendingItem, loadingItemId, status},
      displayStatus,
    } = variablesStore;

    const scopeId =
      variablesStore.scopeId ??
      modificationsStore.getNewScopeIdForFlowNode(
        flowNodeSelectionStore.state.selection?.flowNodeId
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
        }
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

    const isAddMode = initialValues?.name === '' && initialValues?.value === '';

    if (displayStatus === 'no-content') {
      return null;
    }

    return (
      <VariablesContent>
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
            scopes={['write']}
            resourceBasedRestrictions={{
              scopes: ['UPDATE_PROCESS_INSTANCE'],
              permissions: processInstanceDetailsStore.getPermissions(),
            }}
          >
            <Footer>
              {processInstanceDetailsStore.isRunning && (
                <>
                  {pendingItem !== null && <div>pending variable</div>}
                  {isAddMode && pendingItem === null && <div>new variable</div>}
                </>
              )}
              {!isAddMode && pendingItem === null && (
                <AddVariableButton
                  onClick={() => {
                    form.reset({name: '', value: ''});
                  }}
                  disabled={
                    status === 'first-fetch' ||
                    !isViewMode ||
                    (flowNodeSelectionStore.isRootNodeSelected
                      ? !processInstanceDetailsStore.isRunning
                      : !flowNodeMetaDataStore.isSelectedInstanceRunning) ||
                    loadingItemId !== null
                  }
                />
              )}
            </Footer>
          </Restricted>
        )}
      </VariablesContent>
    );
  }
);

export default Variables;
