/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';
import {TextInput, AddTextarea} from '../styled';
import {Container, Fields, Name, Value, EditButtonsContainer} from './styled';
import {Field, useForm, useFormState} from 'react-final-form';
import {EditButtons} from '../EditButtons';
import {
  validateNameCharacters,
  validateNameComplete,
  validateValueComplete,
} from '../validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {InjectAriaInvalid} from 'modules/components/InjectAriaInvalid';
import {JSONEditorModal} from '../JSONEditorModal';

const NewVariable: React.FC = () => {
  const formState = useFormState();
  const form = useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);

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
                  onChange={input.onChange}
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
                  onChange={input.onChange}
                />
              </InjectAriaInvalid>
            )}
          </Field>
        </Value>
      </Fields>
      <EditButtonsContainer>
        <EditButtons onModalButtonClick={() => setIsModalVisible(true)} />
      </EditButtonsContainer>
      <JSONEditorModal
        title={
          formState.values?.name
            ? `Edit Variable "${formState.values?.name}"`
            : 'Edit a new Variable'
        }
        value={formState.values?.value}
        onClose={() => {
          setIsModalVisible(false);
        }}
        onSave={(value) => {
          form.change('value', value);
          setIsModalVisible(false);
        }}
        isModalVisible={isModalVisible}
      />
    </Container>
  );
};

export {NewVariable};
