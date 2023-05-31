/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {observer} from 'mobx-react';
import {
  validateVariableNameCharacters,
  validateVariableNameComplete,
  validateVariableValueComplete,
  validateVariableValueValid,
} from 'modules/validators';
import {Field, useForm, useFormState} from 'react-final-form';
import {JSONEditorModal} from 'modules/components/Carbon/JSONEditorModal';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {tracking} from 'modules/tracking';
import {Title} from 'modules/components/Carbon/FiltersPanel/styled';
import {Popup} from '@carbon/react/icons';
import {TextInputField} from 'modules/components/Carbon/TextInputField';
import {IconTextInputField} from 'modules/components/Carbon/IconTextInputField';
import {createPortal} from 'react-dom';
import {Stack} from '@carbon/react';

const Variable: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const formState = useFormState();
  const form = useForm();

  return (
    <>
      <Title>Variable</Title>
      <Stack gap={5}>
        <Field
          name="variableName"
          validate={mergeValidators(
            validateVariableNameCharacters,
            validateVariableNameComplete
          )}
        >
          {({input, meta}) => (
            <TextInputField
              {...input}
              id="variableName"
              size="sm"
              data-testid="optional-filter-variable-name"
              labelText="Name"
              autoFocus
            />
          )}
        </Field>
        <Field
          name="variableValue"
          validate={mergeValidators(
            validateVariableValueComplete,
            validateVariableValueValid
          )}
        >
          {({input, meta}) => (
            <IconTextInputField
              {...input}
              id="variableValue"
              size="sm"
              placeholder="in JSON format"
              data-testid="optional-filter-variable-value"
              labelText="Value"
              buttonLabel="Open JSON editor modal"
              onIconClick={() => {
                setIsModalVisible(true);
                tracking.track({
                  eventName: 'json-editor-opened',
                  variant: 'search-variable',
                });
              }}
              Icon={Popup}
            />
          )}
        </Field>
      </Stack>
      {createPortal(
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
        />,
        document.body
      )}
    </>
  );
});

export {Variable};
