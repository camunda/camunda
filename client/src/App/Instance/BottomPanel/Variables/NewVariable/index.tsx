/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {TextInput, AddTextarea} from '../styled';
import {Container, Fields, Name, Value, EditButtonsContainer} from './styled';

import {Field} from 'react-final-form';

import {EditButtons} from '../EditButtons';
import {
  validateNameCharacters,
  validateNameComplete,
  validateValueComplete,
} from '../validators';

import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {InjectAriaInvalid} from 'modules/components/InjectAriaInvalid';

const NewVariable: React.FC = () => {
  return (
    <Container data-testid="add-key-row">
      <Fields>
        <Name>
          <Field
            name="name"
            validate={mergeValidators(
              validateNameCharacters,
              validateNameComplete
            )}
            allowNull={false}
          >
            {({input}) => (
              <InjectAriaInvalid name={input.name}>
                <TextInput
                  {...input}
                  autoFocus
                  type="text"
                  placeholder="Name"
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                    input.onChange(e);
                  }}
                />
              </InjectAriaInvalid>
            )}
          </Field>
        </Name>
        <Value>
          <Field name="value" validate={validateValueComplete}>
            {({input}) => (
              <InjectAriaInvalid name={input.name}>
                <AddTextarea
                  {...input}
                  placeholder="Value"
                  hasAutoSize
                  minRows={1}
                  maxRows={4}
                  onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                    input.onChange(e);
                  }}
                />
              </InjectAriaInvalid>
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
