/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Field, useForm} from 'react-final-form';
import {createNewVariableFieldName} from '../createVariableFieldName';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {
  validateNameCharacters,
  validateModifiedNameComplete,
  validateModifiedNameNotDuplicate,
} from '../validators';
import {TextInputField} from 'modules/components/TextInputField';
import {useVariableFormFields} from './useVariableFormFields';
import {createModification} from './createModification';
import {Layer} from '@carbon/react';
import {useEffect, useRef} from 'react';

type Props = {
  variableName: string;
  scopeId: string | null;
};

const Name: React.FC<Props> = ({variableName, scopeId}) => {
  const form = useForm();

  const {currentName, currentValue, currentId, areFormFieldsValid} =
    useVariableFormFields(variableName);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef?.current?.focus();
  }, []);

  return (
    <Layer>
      <Field
        name={createNewVariableFieldName(variableName, 'name')}
        validate={mergeValidators(
          validateNameCharacters,
          validateModifiedNameComplete,
          validateModifiedNameNotDuplicate,
        )}
        allowNull={false}
        parse={(value) => value}
      >
        {({input}) => (
          <TextInputField
            {...input}
            ref={inputRef}
            data-testid="new-variable-name"
            type="text"
            placeholder="Name"
            id={createNewVariableFieldName(variableName, 'name')}
            size="sm"
            hideLabel
            labelText="Name"
            onBlur={() => {
              form.mutators?.triggerValidation?.(
                createNewVariableFieldName(variableName, 'name'),
              );

              input.onBlur();

              createModification({
                scopeId,
                areFormFieldsValid,
                id: currentId,
                name: currentName,
                value: currentValue,
              });
            }}
          />
        )}
      </Field>
    </Layer>
  );
};

export {Name};
