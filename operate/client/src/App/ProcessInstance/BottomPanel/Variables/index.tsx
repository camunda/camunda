/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useEffect} from 'react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {VariablesContent, EmptyMessageWrapper} from './styled';
import {observer} from 'mobx-react';
import {computed, reaction} from 'mobx';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {useForm, useFormState} from 'react-final-form';
import {Restricted} from 'modules/components/Restricted';
import {modificationsStore} from 'modules/stores/modifications';
import {useFieldArray} from 'react-final-form-arrays';
import {VariableFormValues} from 'modules/types/variables';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {VariablesTable} from './VariablesTable';
import {Footer} from './Footer';
import {Skeleton} from './Skeleton';

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
            scopes={['write']}
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
