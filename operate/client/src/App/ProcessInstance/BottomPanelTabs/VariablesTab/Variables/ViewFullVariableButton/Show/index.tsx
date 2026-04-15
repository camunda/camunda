/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {useVariable} from 'modules/queries/variables/useVariable';
import {tracking} from 'modules/tracking';
import {useExistingVariableEditor} from 'modules/hooks/useExistingVariableEditor';
import {useForm} from 'react-final-form';
import type {ViewFullVariableButtonShowProps} from '../types';
import {InlineLoading} from '../../Operations/styled';
import {MaximizeButton} from '../MaximizeButton';
import {Button} from '@carbon/react';

const ViewFullVariableButtonShow: React.FC<ViewFullVariableButtonShowProps> = ({
  variableName,
  variableKey,
  buttonLabel,
  variableValue,
  shouldSubmitOnApply,
  canEdit = false,
}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const {data, isLoading} = useVariable(variableKey, {
    enabled: isModalVisible,
  });
  const fullVariableValue = data?.value;

  const form = useForm();
  const variableEditor = useExistingVariableEditor(variableName, variableValue);

  const handleOpen = () => {
    setIsModalVisible(true);
  };

  return isLoading ? (
    <InlineLoading data-testid="variable-operation-spinner" />
  ) : (
    <>
      {buttonLabel ? (
        <Button kind="ghost" size="sm" onClick={handleOpen}>
          {buttonLabel}
        </Button>
      ) : (
        <MaximizeButton onClick={handleOpen} />
      )}
      {fullVariableValue !== undefined && (
        <JSONEditorModal
          value={fullVariableValue}
          isVisible={isModalVisible}
          readOnly
          allowModeToggle={canEdit}
          onClose={() => setIsModalVisible(false)}
          onApply={(value) => {
            form.reset({name: variableName, value: fullVariableValue});
            form.change(variableEditor.fieldName, value);
            if (shouldSubmitOnApply) {
              form.submit();
            }
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-saved',
              variant: 'edit-variable',
            });

            const initialValue = variableEditor.getInitialValue(data);
            variableEditor.createModification({
              scopeId: variableEditor.variableScopeKey,
              name: variableName,
              oldValue: initialValue,
              newValue: value ?? '',
              isDirty: initialValue !== value,
              isValid: variableEditor.isValid ?? false,
              selectedElementName: variableEditor.selectedElementName,
            });
          }}
          title={`Full value of ${variableName}`}
          editModeTitle={`Edit Variable "${variableName}"`}
        />
      )}
    </>
  );
};

export {ViewFullVariableButtonShow};
