/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {VariableHeader, VariableNameField, VariableValueField} from './styled';
import {observer} from 'mobx-react';
import {
  validateVariableNameCharacters,
  validateVariableNameComplete,
  validateVariableValueComplete,
} from 'modules/validators';
import {Field, useForm, useFormState} from 'react-final-form';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {OptionalFilter} from './OptionalFilter';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';

const Variable: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const formState = useFormState();
  const form = useForm();

  return (
    <OptionalFilter
      name="variable"
      filterList={['variableName', 'variableValue']}
    >
      <VariableHeader appearance="emphasis">Variable</VariableHeader>
      <Field
        name="variableName"
        validate={mergeValidators(
          validateVariableNameCharacters,
          validateVariableNameComplete
        )}
      >
        {({input, meta}) => (
          <VariableNameField
            {...input}
            type="text"
            data-testid="filter-variable-name"
            label="Name"
            autoFocus
            shouldDebounceError={!meta.dirty && formState.dirty}
          />
        )}
      </Field>
      <Field name="variableValue" validate={validateVariableValueComplete}>
        {({input, meta}) => (
          <VariableValueField
            {...input}
            type="text"
            placeholder="in JSON format"
            data-testid="filter-variable-value"
            label="Value"
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
      <JSONEditorModal
        title={`Edit Variable Value`}
        value={formState.values?.variableValue}
        onClose={() => {
          setIsModalVisible(false);
        }}
        onSave={(value) => {
          form.change('variableValue', value);
          setIsModalVisible(false);
        }}
        isModalVisible={isModalVisible}
      />
    </OptionalFilter>
  );
});

export {Variable};
