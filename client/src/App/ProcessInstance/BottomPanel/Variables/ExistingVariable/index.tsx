/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  ValueField,
  TD,
  VariableName,
  EditInputTD,
  EditInputContainer,
} from '../styled';
import {validateValueComplete, validateValueValid} from '../validators';
import {Field, useField, useForm, useFormState} from 'react-final-form';
import {useRef, useState} from 'react';
import {EditButtons} from '../EditButtons';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {tracking} from 'modules/tracking';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {createVariableFieldName} from '../createVariableFieldName';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';

type Props = {
  variableName: string;
  variableValue: string;
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
    'EDIT_VARIABLE'
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

const ExistingVariable: React.FC<Props> = observer(
  ({variableName, variableValue}) => {
    const {isModificationModeEnabled} = modificationsStore;
    const formState = useFormState();
    const [isModalVisible, setIsModalVisible] = useState(false);
    const form = useForm();

    const editInputTDRef = useRef<HTMLTableCellElement | null>(null);

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
      'EDIT_VARIABLE'
    );

    const initialValue =
      lastEditModification !== undefined
        ? lastEditModification?.newValue
        : variableValue;

    return (
      <>
        <TD>
          <VariableName title={variableName}>{variableName}</VariableName>
        </TD>

        <EditInputTD ref={editInputTDRef}>
          <EditInputContainer>
            <Field
              name={fieldName}
              initialValue={initialValue}
              validate={mergeValidators(
                validateValueComplete,
                validateValueValid
              )}
              parse={(value) => value}
            >
              {({input}) => (
                <ValueField
                  {...input}
                  type="text"
                  placeholder="Value"
                  data-testid="edit-variable-value"
                  fieldSuffix={{
                    type: 'icon',
                    icon: 'window',
                    press: () => {
                      setIsModalVisible(true);
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'edit-variable',
                      });
                    },
                    tooltip: 'Open JSON editor modal',
                  }}
                  shouldDebounceError={false}
                  autoFocus={!isModificationModeEnabled}
                  onBlur={(e) => {
                    createModification({
                      scopeId: variablesStore.scopeId,
                      name: variableName,
                      oldValue: variableValue,
                      newValue: input.value ?? '',
                      isDirty: variableValue !== input.value ?? false,
                      isValid: isValid ?? false,
                    });

                    input.onBlur(e);
                  }}
                />
              )}
            </Field>
            {!isModificationModeEnabled && <EditButtons />}
          </EditInputContainer>
        </EditInputTD>
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
      </>
    );
  }
);

export {ExistingVariable};
