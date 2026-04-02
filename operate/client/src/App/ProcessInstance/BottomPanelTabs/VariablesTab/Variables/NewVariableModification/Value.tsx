/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, useForm} from 'react-final-form';
import {createNewVariableFieldName} from '../createVariableFieldName';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {
  validateModifiedValueComplete,
  validateModifiedValueValid,
} from '../validators';
import {useVariableFormFields} from './useVariableFormFields';
import {createModification} from './createModification';
import {Layer} from '@carbon/react';
import {useSelectedElementName} from 'modules/hooks/elementSelection';
import {TextInputField} from 'modules/components/TextInputField';

type Props = {
  variableName: string;
  scopeId: string | null;
};

const Value: React.FC<Props> = ({variableName, scopeId}) => {
  const form = useForm();
  const valueFieldName = createNewVariableFieldName(variableName, 'value');
  const selectedElementName = useSelectedElementName() || '';

  const {currentName, currentValue, currentId, areFormFieldsValid} =
    useVariableFormFields(variableName);

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
          <TextInputField
            {...input}
            data-testid="new-variable-value"
            size="sm"
            type="text"
            id={valueFieldName}
            hideLabel
            labelText="Value"
            placeholder="Value"
            onBlur={() => {
              form.mutators?.triggerValidation?.(valueFieldName);
              input.onBlur();

              createModification({
                scopeId,
                areFormFieldsValid,
                id: currentId,
                name: currentName,
                value: currentValue,
                selectedElementName,
              });
            }}
          />
        )}
      </Field>
    </Layer>
  );
};

export {Value};
