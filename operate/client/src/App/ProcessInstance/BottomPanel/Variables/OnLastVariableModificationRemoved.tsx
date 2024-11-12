/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
