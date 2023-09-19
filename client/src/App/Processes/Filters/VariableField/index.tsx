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
  validateVariableValuesComplete,
  validateMultipleVariableValuesValid,
  validateVariableValueValid,
} from 'modules/validators';
import {Field, useForm, useFormState} from 'react-final-form';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {tracking} from 'modules/tracking';
import {Title} from 'modules/components/FiltersPanel/styled';
import {Popup} from '@carbon/react/icons';
import {TextInputField} from 'modules/components/TextInputField';
import {createPortal} from 'react-dom';
import {Stack} from '@carbon/react';
import {IconTextAreaField} from 'modules/components/IconTextAreaField';
import {IS_VARIABLE_VALUE_IN_FILTER_ENABLED} from 'modules/feature-flags';
import {IconTextInputField} from 'modules/components/IconTextInputField';
import {Toggle, VariableValueContainer} from './styled';

const Variable: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [isInMultipleMode, setIsInMultipleMode] = useState(false);
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
            validateVariableNameComplete,
          )}
        >
          {({input}) => (
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
        <VariableValueContainer>
          <Field
            name="variableValues"
            validate={mergeValidators(
              validateVariableValuesComplete,
              isInMultipleMode
                ? validateMultipleVariableValuesValid
                : validateVariableValueValid,
            )}
          >
            {({input}) => {
              if (isInMultipleMode) {
                return (
                  <IconTextAreaField
                    {...input}
                    id="variableValues"
                    placeholder="separated by comma"
                    data-testid="optional-filter-variable-value"
                    labelText="Values"
                    buttonLabel="Open modal"
                    onIconClick={() => {}}
                    Icon={Popup}
                  />
                );
              } else {
                return (
                  <IconTextInputField
                    {...input}
                    id="variableValues"
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
                );
              }
            }}
          </Field>
          {IS_VARIABLE_VALUE_IN_FILTER_ENABLED && (
            <Toggle
              id="multiple-mode"
              size="sm"
              labelA="Multiple"
              labelB="Multiple"
              aria-label="Multiple"
              toggled={isInMultipleMode}
              onToggle={() => {
                form.change('variableValues', '');
                setIsInMultipleMode(!isInMultipleMode);
              }}
            />
          )}
        </VariableValueContainer>
      </Stack>

      {createPortal(
        <JSONEditorModal
          isVisible={isModalVisible}
          title="Edit Variable Value"
          value={formState.values?.variableValues}
          onClose={() => {
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-closed',
              variant: 'search-variable',
            });
          }}
          onApply={(value) => {
            form.change('variableValues', value);
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-saved',
              variant: 'search-variable',
            });
          }}
        />,
        document.body,
      )}
    </>
  );
});

export {Variable};
