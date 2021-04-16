/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {AddButtonsTD, EditInputTD, TextInput, AddTextarea} from './styled';
import {Field} from 'react-final-form';
import {useRef} from 'react';

import {EditButtons} from './EditButtons';
import {TR} from './VariablesTable';
import {isFieldValid} from 'modules/utils/isFieldValid';
import {
  handleVariableNameFieldValidation,
  handleVariableValueFieldValidation,
} from './validations';

type Props = {
  onHeightChange: (itemRef: React.RefObject<HTMLTableDataCellElement>) => void;
};

const NewVariable: React.FC<Props> = ({onHeightChange}) => {
  const editInputTDRef = useRef<HTMLTableDataCellElement>(null);

  return (
    <TR data-testid="add-key-row">
      <EditInputTD>
        <Field
          name="name"
          validate={handleVariableNameFieldValidation}
          allowNull={false}
        >
          {({input, meta}) => (
            <TextInput
              {...input}
              autoFocus
              type="text"
              placeholder="Name"
              $hasError={!isFieldValid(meta)}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                input.onChange(e);
              }}
            />
          )}
        </Field>
      </EditInputTD>
      <EditInputTD ref={editInputTDRef}>
        <Field name="value" validate={handleVariableValueFieldValidation}>
          {({input, meta}) => (
            <AddTextarea
              {...input}
              placeholder="Value"
              hasAutoSize
              minRows={1}
              maxRows={4}
              $hasError={!isFieldValid(meta)}
              onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                input.onChange(e);
              }}
              onHeightChange={() => {
                onHeightChange(editInputTDRef);
              }}
            />
          )}
        </Field>
      </EditInputTD>
      <AddButtonsTD>
        <EditButtons />
      </AddButtonsTD>
    </TR>
  );
};

export {NewVariable};
