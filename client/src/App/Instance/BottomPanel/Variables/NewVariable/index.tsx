/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {TextInput, AddTextarea} from '../styled';
import {Container, Fields, Name, Value, EditButtonsContainer} from './styled';

import {Field, useFormState} from 'react-final-form';

import {EditButtons} from '../EditButtons';
import {isFieldValid} from 'modules/utils/isFieldValid';
import {
  handleVariableNameFieldValidation,
  handleVariableValueFieldValidation,
} from '../validations';

const NewVariable: React.FC = () => {
  const formState = useFormState();

  return (
    <Container data-testid="add-key-row">
      <Fields>
        <Name>
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
                $hasError={!isFieldValid(meta, formState.submitErrors?.name)}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                  input.onChange(e);
                }}
              />
            )}
          </Field>
        </Name>
        <Value>
          <Field name="value" validate={handleVariableValueFieldValidation}>
            {({input, meta}) => (
              <AddTextarea
                {...input}
                placeholder="Value"
                hasAutoSize
                minRows={1}
                maxRows={4}
                $hasError={!isFieldValid(meta, formState.submitErrors?.value)}
                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                  input.onChange(e);
                }}
              />
            )}
          </Field>
        </Value>
      </Fields>
      <EditButtonsContainer>
        <EditButtons />
      </EditButtonsContainer>
    </Container>
  );
};

export {NewVariable};
