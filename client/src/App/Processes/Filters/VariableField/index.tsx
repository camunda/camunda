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
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {tracking} from 'modules/tracking';

const Variable: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const formState = useFormState();
  const form = useForm();

  return (
    <>
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
            data-testid="optional-filter-variable-name"
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
            data-testid="optional-filter-variable-value"
            label="Value"
            fieldSuffix={{
              type: 'icon',
              icon: 'window',
              press: () => {
                setIsModalVisible(true);
                tracking.track({
                  eventName: 'json-editor-opened',
                  variant: 'search-variable',
                });
              },
              tooltip: 'Open JSON editor modal',
            }}
            shouldDebounceError={!meta.dirty && formState.dirty}
          />
        )}
      </Field>
      <JSONEditorModal
        isVisible={isModalVisible}
        title="Edit Variable Value"
        value={formState.values?.variableValue}
        onClose={() => {
          setIsModalVisible(false);
          tracking.track({
            eventName: 'json-editor-closed',
            variant: 'search-variable',
          });
        }}
        onApply={(value) => {
          form.change('variableValue', value);
          setIsModalVisible(false);
          tracking.track({
            eventName: 'json-editor-saved',
            variant: 'search-variable',
          });
        }}
      />
    </>
  );
});

export {Variable};
