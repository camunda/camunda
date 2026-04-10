/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, useForm} from 'react-final-form';
import {useFieldError} from 'modules/hooks/useFieldError';
import {createNewVariableFieldName} from '../createVariableFieldName';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {
  validateModifiedValueComplete,
  validateModifiedValueValid,
} from '../validators';
import {useVariableFormFields} from './useVariableFormFields';
import {createModification} from './createModification';
import {useSelectedElementName} from 'modules/hooks/elementSelection';
import {InlineJsonEditor} from 'modules/components/InlineJsonEditor';
import {Layer} from '@carbon/react';

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
  const fieldError = useFieldError(valueFieldName);

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
          <InlineJsonEditor
            {...input}
            label="Value"
            data-testid="new-variable-value"
            id={valueFieldName}
            fieldError={fieldError}
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
