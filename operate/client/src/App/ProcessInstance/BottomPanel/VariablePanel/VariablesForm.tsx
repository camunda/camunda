/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {computed} from 'mobx';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {type VariableFormValues} from 'modules/types/variables';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {type FormRenderProps} from 'react-final-form';
import {AddVariableButton, Form, VariablesContainer} from './styled';
import {Variables} from '../Variables';
import {
  useIsPlaceholderSelected,
  useIsRootNodeSelected,
} from 'modules/hooks/flowNodeSelection';
import {hasPendingAddOrMoveModification} from 'modules/utils/modifications';

const VariablesForm: React.FC<FormRenderProps<VariableFormValues>> = observer(
  ({handleSubmit, form, values}) => {
    const isPlaceholderSelected = useIsPlaceholderSelected();
    const isRootNodeSelected = useIsRootNodeSelected();
    const hasEmptyNewVariable = (values?: VariableFormValues) =>
      values?.newVariables?.some(
        (variable) =>
          variable === undefined ||
          variable.name === undefined ||
          variable.value === undefined,
      );

    const {isModificationModeEnabled} = modificationsStore;

    const isVariableModificationAllowed = computed(() => {
      switch (true) {
        case !isModificationModeEnabled:
          return false;
        case isRootNodeSelected:
          return hasPendingAddOrMoveModification();
        default:
          return isPlaceholderSelected;
      }
    });

    const isModificationAllowed = isVariableModificationAllowed.get();

    return (
      <Form onSubmit={handleSubmit}>
        {isModificationAllowed && (
          <AddVariableButton
            onClick={() => {
              form.mutators.push?.('newVariables', {
                id: generateUniqueID(),
              });
            }}
            disabled={
              form.getState().submitting ||
              form.getState().hasValidationErrors ||
              form.getState().validating ||
              hasEmptyNewVariable(values)
            }
          />
        )}
        <VariablesContainer>
          <Variables isVariableModificationAllowed={isModificationAllowed} />
        </VariablesContainer>
      </Form>
    );
  },
);

export {VariablesForm};
