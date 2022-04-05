/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';
import {Field, useForm, useFormState} from 'react-final-form';
import {Container, NameField, ValueField, EditButtonsContainer} from './styled';
import {EditButtons} from '../EditButtons';
import {
  validateNameCharacters,
  validateNameComplete,
  validateValueComplete,
} from '../validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';

const NewVariable: React.FC = () => {
  const formState = useFormState();
  const form = useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);

  return (
    <Container data-testid="add-key-row">
      <Field
        name="name"
        validate={mergeValidators(validateNameCharacters, validateNameComplete)}
        allowNull={false}
        parse={(value) => value}
      >
        {({input, meta}) => (
          <NameField
            {...input}
            type="text"
            placeholder="Name"
            data-testid="add-variable-name"
            shouldDebounceError={!meta.dirty && formState.dirty}
            autoFocus={true}
          />
        )}
      </Field>
      <Field
        name="value"
        validate={validateValueComplete}
        parse={(value) => value}
      >
        {({input, meta}) => (
          <ValueField
            {...input}
            type="text"
            placeholder="Value"
            data-testid="add-variable-value"
            fieldSuffix={{
              type: 'icon',
              icon: 'window',
              press: () => {
                setIsModalVisible(true);
              },
              tooltip: 'Open JSON editor modal',
            }}
            shouldDebounceError={!meta.dirty && formState.dirty}
          />
        )}
      </Field>
      <EditButtonsContainer>
        <EditButtons />
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
