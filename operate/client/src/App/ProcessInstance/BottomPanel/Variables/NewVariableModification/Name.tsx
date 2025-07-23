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
  validateNameCharacters,
  validateModifiedNameComplete,
  validateModifiedNameNotDuplicate,
} from '../validators';
import {TextInputField} from 'modules/components/TextInputField';
import {useVariableFormFields} from './useVariableFormFields';
import {createModification} from './createModification';
import {Layer} from '@carbon/react';
import {useEffect, useRef} from 'react';
import {useSelectedFlowNodeName} from 'modules/hooks/flowNodeSelection';
import {useVariables} from 'modules/queries/variables/useVariables';

type Props = {
  variableName: string;
  scopeId: string | null;
};

const Name: React.FC<Props> = ({variableName, scopeId}) => {
  const form = useForm();

  const {currentName, currentValue, currentId, areFormFieldsValid} =
    useVariableFormFields(variableName);
  const selectedFlowNodeName = useSelectedFlowNodeName() || '';
  const inputRef = useRef<HTMLInputElement>(null);
  const {data: variablesData} = useVariables();
  const allVariables =
    variablesData?.pages.flatMap((page) => (page.items ? page.items : [])) ??
    [];

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
          validateModifiedNameNotDuplicate(allVariables),
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
                selectedFlowNodeName,
              });
            }}
          />
        )}
      </Field>
    </Layer>
  );
};

export {Name};
