/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  TD,
  VariableName,
  EditInputTD,
  EditTextarea,
  EditButtonsTD,
} from './styled';
import {Field} from 'react-final-form';
import {useRef} from 'react';

import {EditButtons} from './EditButtons';
import {isFieldValid} from 'modules/utils/isFieldValid';
import {handleVariableValueFieldValidation} from './validations';

type Props = {
  variableName: string;
  variableValue: string;
  onHeightChange: (itemRef: React.RefObject<HTMLTableDataCellElement>) => void;
};

const ExistingVariable: React.FC<Props> = ({
  variableName,
  variableValue,
  onHeightChange,
}) => {
  const editInputTDRef = useRef<HTMLTableDataCellElement>(null);

  return (
    <>
      <TD isBold={true}>
        <Field name="name" initialValue={variableName}>
          {() => {
            return (
              <VariableName title={variableName}>{variableName}</VariableName>
            );
          }}
        </Field>
      </TD>

      <EditInputTD ref={editInputTDRef}>
        <Field
          name="value"
          initialValue={variableValue}
          validate={handleVariableValueFieldValidation}
        >
          {({input, meta}) => {
            return (
              <EditTextarea
                {...input}
                autoFocus
                hasAutoSize
                minRows={1}
                maxRows={4}
                data-testid="edit-value"
                placeholder="Value"
                $hasError={!isFieldValid(meta)}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                  input.onChange(e);
                }}
                onHeightChange={() => {
                  onHeightChange(editInputTDRef);
                }}
              />
            );
          }}
        </Field>
      </EditInputTD>
      <EditButtonsTD>
        <EditButtons />
      </EditButtonsTD>
    </>
  );
};

export {ExistingVariable};
