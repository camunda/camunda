/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Field, useForm, useFormState} from 'react-final-form';
import get from 'lodash/get';
import {createNewVariableFieldName} from '../createVariableFieldName';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {
  validateModifiedValueComplete,
  validateModifiedValueValid,
} from '../validators';
import {IconTextInputField} from 'modules/components/IconTextInputField';
import {Popup} from '@carbon/react/icons';
import {useVariableFormFields} from './useVariableFormFields';
import {createModification} from './createModification';
import {Layer} from '@carbon/react';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';

type Props = {
  variableName: string;
  scopeId: string | null;
};

const Value: React.FC<Props> = ({variableName, scopeId}) => {
  const formState = useFormState();
  const form = useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const valueFieldName = createNewVariableFieldName(variableName, 'value');
  const {data: businessObjects} = useBusinessObjects();

  const {
    currentName,
    currentValue,
    currentId,
    isNameFieldValid,
    areFormFieldsValid,
  } = useVariableFormFields(variableName);

  return (
    <Layer>
      <Field
        name={valueFieldName}
        validate={mergeValidators(
          validateModifiedValueComplete,
          validateModifiedValueValid,
        )}
        parse={(value) => value}
      >
        {({input}) => (
          <IconTextInputField
            {...input}
            data-testid="new-variable-value"
            size="sm"
            type="text"
            id={valueFieldName}
            hideLabel
            labelText="Value"
            placeholder="Value"
            buttonLabel="Open JSON editor modal"
            tooltipPosition="left"
            onIconClick={() => {
              setIsModalVisible(true);
            }}
            Icon={Popup}
            onBlur={() => {
              form.mutators?.triggerValidation?.(valueFieldName);
              input.onBlur();

              createModification({
                scopeId,
                areFormFieldsValid,
                id: currentId,
                name: currentName,
                value: currentValue,
                businessObjects,
              });
            }}
          />
        )}
      </Field>

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
                scopeId,
                areFormFieldsValid:
                  (isNameFieldValid &&
                    form.getFieldState(valueFieldName)?.valid) ??
                  false,
                id: currentId,
                name: currentName,
                value: value,
              });
            }
          }}
        />
      )}
    </Layer>
  );
};

export {Value};
