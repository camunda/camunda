/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {variablesStore} from 'modules/stores/variables';
import {useForm} from 'react-final-form';
import {createVariableFieldName} from './createVariableFieldName';
import {reaction} from 'mobx';

const OnLastVariableModificationRemoved: React.FC = observer(() => {
  const {scopeId} = variablesStore;
  const form = useForm();

  useEffect(() => {
    const disposer = reaction(
      () => modificationsStore.state.lastRemovedModification,
      (lastRemovedModification) => {
        if (
          lastRemovedModification === undefined ||
          lastRemovedModification.type === 'token'
        ) {
          return;
        }

        const {payload} = lastRemovedModification;

        if (payload.operation === 'EDIT_VARIABLE') {
          const {scopeId: removedVariableScopeId, id, name, oldValue} = payload;

          if (removedVariableScopeId !== scopeId) {
            return;
          }

          const lastEditModification =
            modificationsStore.getLastVariableModification(
              scopeId,
              id,
              'EDIT_VARIABLE'
            );

          form.change(
            createVariableFieldName(name),
            lastEditModification !== undefined
              ? lastEditModification.newValue
              : oldValue
          );
        }
      }
    );

    return () => {
      disposer();
    };
  }, [scopeId, form]);

  return null;
});

export {OnLastVariableModificationRemoved};
