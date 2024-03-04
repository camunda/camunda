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

import React, {useEffect} from 'react';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {variablesStore} from 'modules/stores/variables';
import {useForm, useFormState} from 'react-final-form';
import {createVariableFieldName} from './createVariableFieldName';
import {reaction} from 'mobx';
import {VariableFormValues} from 'modules/types/variables';
import {useFieldArray} from 'react-final-form-arrays';

const OnLastVariableModificationRemoved: React.FC = observer(() => {
  const {scopeId} = variablesStore;
  const form = useForm();
  const formState = useFormState<VariableFormValues>();
  const fieldArray = useFieldArray('newVariables');
  const newVariables = formState.values.newVariables;

  useEffect(() => {
    const disposer = reaction(
      () => modificationsStore.state.lastRemovedModification,
      (lastRemovedModification) => {
        if (
          lastRemovedModification?.modification === undefined ||
          lastRemovedModification.modification.type === 'token'
        ) {
          return;
        }

        const {payload} = lastRemovedModification.modification;

        if (payload.operation === 'EDIT_VARIABLE') {
          const {scopeId: removedVariableScopeId, id, name, oldValue} = payload;

          if (removedVariableScopeId !== scopeId) {
            return;
          }

          const lastEditModification =
            modificationsStore.getLastVariableModification(
              scopeId,
              id,
              'EDIT_VARIABLE',
            );

          form.change(
            createVariableFieldName(name),
            lastEditModification !== undefined
              ? lastEditModification.newValue
              : oldValue,
          );
        } else if (payload.operation === 'ADD_VARIABLE') {
          const {scopeId: removedVariableScopeId, id} = payload;

          if (
            removedVariableScopeId !== scopeId ||
            newVariables === undefined
          ) {
            return;
          }

          const lastAddModification =
            modificationsStore.getLastVariableModification(
              scopeId,
              id,
              'ADD_VARIABLE',
            );

          const index = newVariables.findIndex((field) => field.id === id);

          if (index !== -1 && lastRemovedModification.source !== 'variables') {
            if (lastAddModification !== undefined) {
              fieldArray.fields.update(index, {
                id: lastAddModification.id,
                name: lastAddModification.name,
                value: lastAddModification.newValue,
              });
            } else {
              fieldArray.fields.remove(index);
            }
          }
        }
      },
    );

    return () => {
      disposer();
    };
  }, [scopeId, form, fieldArray, newVariables]);

  return null;
});

export {OnLastVariableModificationRemoved};
