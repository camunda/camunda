/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {modificationsStore} from 'modules/stores/modifications';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.9';
import {useVariableScopeKey} from 'modules/hooks/variables';
import {useField, useFormState} from 'react-final-form';
import {createVariableFieldName} from '../../App/ProcessInstance/BottomPanelTabs/VariablesTab/Variables/createVariableFieldName';
import {useSelectedElementName} from 'modules/hooks/elementSelection';

const createModification = ({
  scopeId,
  isValid,
  isDirty,
  name,
  oldValue,
  newValue,
  selectedElementName,
}: {
  scopeId: string | null;
  isValid: boolean;
  isDirty: boolean;
  name: string;
  oldValue: string;
  newValue: string;
  selectedElementName: string;
}) => {
  if (
    !modificationsStore.isModificationModeEnabled ||
    scopeId === null ||
    !isValid ||
    newValue === ''
  ) {
    return;
  }

  const lastEditModification = modificationsStore.getLastVariableModification(
    scopeId,
    name,
    'EDIT_VARIABLE',
  );

  if (
    lastEditModification?.newValue !== newValue &&
    (isDirty ||
      (lastEditModification !== undefined &&
        lastEditModification.newValue !== newValue))
  ) {
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        id: name,
        scopeId,
        elementName: selectedElementName,
        name,
        oldValue,
        newValue,
      },
    });
  }
};

export function useExistingVariableEditor(
  variableName: string,
  variableValue: string,
) {
  const {isModificationModeEnabled} = modificationsStore;

  const variableScopeKey = useVariableScopeKey();

  const formState = useFormState();

  const selectedElementName = useSelectedElementName() || '';

  const pendingEditModification =
    isModificationModeEnabled && variableScopeKey !== null
      ? modificationsStore.getLastVariableModification(
          variableScopeKey,
          variableName,
          'EDIT_VARIABLE',
        )
      : undefined;

  const getInitialValue = (variable?: Variable) => {
    if (pendingEditModification !== undefined) {
      return pendingEditModification.newValue;
    }
    return variable?.value ?? variableValue;
  };

  const fieldName = isModificationModeEnabled
    ? createVariableFieldName(variableName)
    : 'value';

  const {
    meta: {validating, valid},
  } = useField(fieldName);

  const isValid = !validating && valid;

  return {
    isSubmittingForm: formState.submitting,
    fieldValue: formState.values?.[fieldName],
    pendingEditModification,
    fieldName,
    variableScopeKey,
    isValid,
    selectedElementName,
    getInitialValue,
    createModification,
  };
}
