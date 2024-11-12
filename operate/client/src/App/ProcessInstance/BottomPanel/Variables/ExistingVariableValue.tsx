/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {validateValueComplete, validateValueValid} from './validators';
import {Field, useField, useForm, useFormState} from 'react-final-form';
import {useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {tracking} from 'modules/tracking';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {createVariableFieldName} from './createVariableFieldName';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {Popup} from '@carbon/react/icons';
import {LoadingTextfield} from './LoadingTextField';
import {Layer} from '@carbon/react';

type Props = {
  id?: string;
  variableName: string;
  variableValue: string;
  pauseValidation?: boolean;
  onFocus?: () => void;
};

const createModification = ({
  scopeId,
  isValid,
  isDirty,
  name,
  oldValue,
  newValue,
}: {
  scopeId: string | null;
  isValid: boolean;
  isDirty: boolean;
  name: string;
  oldValue: string;
  newValue: string;
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
        flowNodeName: flowNodeSelectionStore.selectedFlowNodeName,
        name,
        oldValue,
        newValue,
      },
    });
  }
};

const ExistingVariableValue: React.FC<Props> = observer(
  ({id, variableName, variableValue, pauseValidation = false, onFocus}) => {
    const {isModificationModeEnabled} = modificationsStore;
    const {loadingItemId} = variablesStore.state;
    const formState = useFormState();
    const [isModalVisible, setIsModalVisible] = useState(false);
    const form = useForm();

    const fieldName = isModificationModeEnabled
      ? createVariableFieldName(variableName)
      : 'value';

    const {
      meta: {validating, valid},
    } = useField(fieldName);

    const isValid = !validating && valid;

    const lastEditModification = modificationsStore.getLastVariableModification(
      variablesStore.scopeId,
      variableName,
      'EDIT_VARIABLE',
    );

    const initialValue =
      lastEditModification !== undefined
        ? lastEditModification?.newValue
        : variableValue;

    return (
      <Layer>
        <Field
          name={fieldName}
          initialValue={initialValue}
          validate={
            pauseValidation
              ? () => undefined
              : mergeValidators(validateValueComplete, validateValueValid)
          }
          parse={(value) => value}
        >
          {({input, meta}) => (
            <LoadingTextfield
              {...input}
              size="sm"
              type="text"
              id={fieldName}
              hideLabel
              labelText="Value"
              placeholder="Value"
              data-testid="edit-variable-value"
              buttonLabel="Open JSON editor modal"
              tooltipPosition="left"
              onIconClick={() => {
                onFocus?.();
                setIsModalVisible(true);
                tracking.track({
                  eventName: 'json-editor-opened',
                  variant: 'edit-variable',
                });
              }}
              Icon={Popup}
              autoFocus={!isModificationModeEnabled || meta.active}
              isLoading={loadingItemId === id}
              onFocus={(event) => {
                if (!meta.active) {
                  onFocus?.();
                  input.onFocus(event);
                }
              }}
              onBlur={(event) => {
                createModification({
                  scopeId: variablesStore.scopeId,
                  name: variableName,
                  oldValue: variableValue,
                  newValue: input.value ?? '',
                  isDirty: variableValue !== input.value,
                  isValid: isValid ?? false,
                });

                input.onBlur(event);
              }}
            />
          )}
        </Field>
        {isModalVisible && (
          <JSONEditorModal
            isVisible={isModalVisible}
            title={`Edit Variable "${variableName}"`}
            value={formState.values?.[fieldName]}
            onClose={() => {
              setIsModalVisible(false);
              tracking.track({
                eventName: 'json-editor-closed',
                variant: 'edit-variable',
              });
            }}
            onApply={(value) => {
              form.change(fieldName, value);
              setIsModalVisible(false);
              tracking.track({
                eventName: 'json-editor-saved',
                variant: 'edit-variable',
              });

              createModification({
                scopeId: variablesStore.scopeId,
                name: variableName,
                oldValue: variableValue,
                newValue: value ?? '',
                isDirty: initialValue !== value,
                isValid: isValid ?? false,
              });
            }}
          />
        )}
      </Layer>
    );
  },
);

export {ExistingVariableValue};
