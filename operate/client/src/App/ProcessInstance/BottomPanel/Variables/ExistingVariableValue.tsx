/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  validateValueComplete,
  validateValueNotEmpty,
  validateValueValid,
} from './validators';
import {Field, useField, useForm, useFormState} from 'react-final-form';
import {useEffect, useState} from 'react';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {tracking} from 'modules/tracking';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {createVariableFieldName} from './createVariableFieldName';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {Popup} from '@carbon/react/icons';
import {LoadingTextfield} from './LoadingTextField';
import {Layer} from '@carbon/react';
import {useSelectedFlowNodeName} from 'modules/hooks/flowNodeSelection';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.8';
import {useVariable} from 'modules/queries/variables/useVariable';
import {notificationsStore} from 'modules/stores/notifications';
import {useVariableScopeKey} from 'modules/hooks/variables';

type Props = {
  id?: string;
  variableName: string;
  variableValue: string;
  isPreview?: boolean;
};

const createModification = ({
  scopeId,
  isValid,
  isDirty,
  name,
  oldValue,
  newValue,
  selectedFlowNodeName,
}: {
  scopeId: string | null;
  isValid: boolean;
  isDirty: boolean;
  name: string;
  oldValue: string;
  newValue: string;
  selectedFlowNodeName: string;
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
        flowNodeName: selectedFlowNodeName,
        name,
        oldValue,
        newValue,
      },
    });
  }
};

const ExistingVariableValue: React.FC<Props> = observer(
  ({id, variableName, variableValue, isPreview}) => {
    const {isModificationModeEnabled} = modificationsStore;
    const formState = useFormState();
    const selectedFlowNodeName = useSelectedFlowNodeName() || '';
    const [isModalVisible, setIsModalVisible] = useState(false);
    const form = useForm();
    const {
      data: variable,
      isLoading,
      error,
    } = useVariable(id!, {
      enabled: isPreview && id !== undefined,
    });
    const variableScopeKey = useVariableScopeKey();

    useEffect(() => {
      if (error) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Variable could not be fetched',
          isDismissable: true,
        });
      }
    }, [error]);

    const fieldName = isModificationModeEnabled
      ? createVariableFieldName(variableName)
      : 'value';

    const {
      meta: {validating, valid},
    } = useField(fieldName);

    const isValid = !validating && valid;

    const getInitialValue = (variable?: Variable) =>
      variable?.value ?? variableValue;

    const isVariableValueUndefined = variable?.value === undefined;
    const pauseValidation = isPreview && isVariableValueUndefined;

    return (
      <Layer>
        <Field
          name={fieldName}
          initialValue={getInitialValue(variable)}
          validate={
            pauseValidation
              ? () => undefined
              : mergeValidators(
                  validateValueComplete,
                  validateValueValid,
                  validateValueNotEmpty,
                )
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
              disabled={formState.submitting}
              labelText="Value"
              placeholder="Value"
              data-testid="edit-variable-value"
              buttonLabel="Open JSON editor modal"
              tooltipPosition="left"
              onIconClick={() => {
                if (formState.submitting) {
                  return;
                }

                setIsModalVisible(true);
                tracking.track({
                  eventName: 'json-editor-opened',
                  variant: 'edit-variable',
                });
              }}
              Icon={Popup}
              autoFocus={!isModificationModeEnabled || meta.active}
              isLoading={isLoading}
              onFocus={(event) => {
                if (!meta.active) {
                  input.onFocus(event);
                }
              }}
              onBlur={(event) => {
                createModification({
                  scopeId: variableScopeKey,
                  name: variableName,
                  oldValue: getInitialValue(variable),
                  newValue: input.value ?? '',
                  isDirty: getInitialValue(variable) !== input.value,
                  isValid: isValid ?? false,
                  selectedFlowNodeName,
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
                scopeId: variableScopeKey,
                name: variableName,
                oldValue: getInitialValue(variable),
                newValue: value ?? '',
                isDirty: getInitialValue(variable) !== value,
                isValid: isValid ?? false,
                selectedFlowNodeName,
              });
            }}
          />
        )}
      </Layer>
    );
  },
);

export {ExistingVariableValue};
