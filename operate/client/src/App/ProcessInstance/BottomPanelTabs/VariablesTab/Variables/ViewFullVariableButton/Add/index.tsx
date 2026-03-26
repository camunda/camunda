/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {useForm, useFormState} from 'react-final-form';
import type {ViewFullVariableButtonAddProps} from '../types';
import get from 'lodash/get';
import {createModification} from '../../NewVariableModification/createModification';
import {createNewVariableFieldName} from '../../createVariableFieldName';
import {useVariableFormFields} from '../../NewVariableModification/useVariableFormFields';
import {useSelectedElementName} from 'modules/hooks/elementSelection';
import {MaximizeButton} from '../MaximizeButton';

const ViewFullVariableButtonAdd: React.FC<ViewFullVariableButtonAddProps> = ({
  variableName,
  scopeId,
}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const valueFieldName = createNewVariableFieldName(variableName, 'value');
  const selectedElementName = useSelectedElementName() || '';

  const form = useForm();
  const formState = useFormState();

  const {currentName, currentId, isNameFieldValid} =
    useVariableFormFields(variableName);

  return (
    <>
      <MaximizeButton
        label="Open JSON editor"
        onClick={() => {
          setIsModalVisible(true);
        }}
      />
      {isModalVisible && (
        <JSONEditorModal
          isVisible={isModalVisible}
          title="Edit a new Variable"
          value={get(formState.values, valueFieldName)}
          onClose={() => {
            setIsModalVisible(false);
          }}
          onApply={(value) => {
            form.change(valueFieldName, value);
            setIsModalVisible(false);
            if (value !== undefined) {
              createModification({
                scopeId: scopeId,
                areFormFieldsValid:
                  (isNameFieldValid &&
                    form.getFieldState(valueFieldName)?.valid) ??
                  false,
                id: currentId,
                name: currentName,
                value: value,
                selectedElementName,
              });
            }
          }}
        />
      )}
    </>
  );
};

export {ViewFullVariableButtonAdd};
