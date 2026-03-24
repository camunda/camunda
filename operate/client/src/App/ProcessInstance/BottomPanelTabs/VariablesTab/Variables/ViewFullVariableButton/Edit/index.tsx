/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
import {useVariable} from 'modules/queries/variables/useVariable';
import {tracking} from 'modules/tracking';
import {useExistingVariableEditor} from 'App/ProcessInstance/BottomPanelTabs/VariablesTab/Variables/useExistingVariableEditor';
import {useForm} from 'react-final-form';
import type {ViewFullVariableButtonEditProps} from '../types';

const ViewFullVariableButtonEdit: React.FC<ViewFullVariableButtonEditProps> = ({
  variableName,
  variableKey,
  variableValue,
}) => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const {data: variable} = useVariable(variableKey, {
    enabled: isModalVisible,
  });

  const form = useForm();
  const variableEditor = useExistingVariableEditor(variableName, variableValue);

  return (
    <>
      <Button
        kind="ghost"
        hasIconOnly
        renderIcon={Maximize}
        size="sm"
        aria-label="Open JSON editor"
        iconDescription="Open JSON editor"
        tooltipPosition="left"
        onClick={() => {
          if (variableEditor.isSubmittingForm) {
            return;
          }

          setIsModalVisible(true);
          tracking.track({
            eventName: 'json-editor-opened',
            variant: 'edit-variable',
          });
        }}
      />
      {isModalVisible && (
        <JSONEditorModal
          value={variableEditor.fieldValue}
          onClose={() => {
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-closed',
              variant: 'edit-variable',
            });
          }}
          onApply={(value) => {
            form.change(variableEditor.fieldName, value);
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-saved',
              variant: 'edit-variable',
            });

            variableEditor.createModification({
              scopeId: variableEditor.variableScopeKey,
              name: variableName,
              oldValue: variableEditor.getInitialValue(variable),
              newValue: value ?? '',
              isDirty: variableEditor.getInitialValue(variable) !== value,
              isValid: variableEditor.isValid ?? false,
              selectedElementName: variableEditor.selectedElementName,
            });
          }}
          isVisible={isModalVisible}
          title={`Edit Variable "${variableName}"`}
        />
      )}
    </>
  );
};

export {ViewFullVariableButtonEdit};
