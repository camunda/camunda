/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {type VariableFormValues} from 'modules/types/variables';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {type FormRenderProps} from 'react-final-form';
import {AddVariableButton, Form, VariablesContainer} from './styled';
import {Variables} from '../Variables';
import {useIsPlaceholderSelected} from 'modules/hooks/flowNodeSelection';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {hasPendingAddOrMoveModification} from 'modules/utils/modifications';

const useIsVariableModificationAllowed = () => {
  const isPlaceholderSelected = useIsPlaceholderSelected();
  const {hasSelection} = useProcessInstanceElementSelection();

  switch (true) {
    case !modificationsStore.isModificationModeEnabled:
      return false;
    case !hasSelection:
      return hasPendingAddOrMoveModification();
    default:
      return isPlaceholderSelected;
  }
};

const VariablesForm: React.FC<FormRenderProps<VariableFormValues>> = observer(
  ({handleSubmit, form, values}) => {
    const isModificationAllowed = useIsVariableModificationAllowed();
    const hasEmptyNewVariable = values?.newVariables?.some(
      (variable) =>
        variable === undefined ||
        variable.name === undefined ||
        variable.value === undefined,
    );

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
              hasEmptyNewVariable
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
