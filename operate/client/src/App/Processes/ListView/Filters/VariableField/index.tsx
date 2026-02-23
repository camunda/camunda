/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
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
import {Maximize} from '@carbon/react/icons';
import {TextInputField} from 'modules/components/TextInputField';
import {createPortal} from 'react-dom';
import {Stack} from '@carbon/react';
import {IconTextAreaField} from 'modules/components/IconTextAreaField';
import {IconTextInputField} from 'modules/components/IconTextInputField';
import {Toggle, VariableValueContainer} from './styled';
import {MultipleValuesModal} from './MultipleValuesModal';
import {variableFilterStore} from 'modules/stores/variableFilter';

const Variable: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const formState = useFormState();
  const form = useForm();

  useEffect(() => {
    return variableFilterStore.reset;
  }, []);

  const {
    state: {isInMultipleMode},
    setIsInMultipleMode,
  } = variableFilterStore;

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
            // this key is needed to trigger validation after the user switched multiple mode
            key={isInMultipleMode ? 'multipleValues' : 'singleValues'}
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
                    placeholder="In JSON format, separated by comma"
                    data-testid="optional-filter-variable-value"
                    labelText="Values"
                    buttonLabel="Open editor modal"
                    onIconClick={() => {
                      setIsModalVisible(true);
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'search-multiple-variables',
                      });
                    }}
                    Icon={Maximize}
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
                    buttonLabel="Open JSON editor"
                    onIconClick={() => {
                      setIsModalVisible(true);
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'search-variable',
                      });
                    }}
                    Icon={Maximize}
                  />
                );
              }
            }}
          </Field>
          <Toggle
            id="multiple-mode"
            size="sm"
            labelA="Multiple"
            labelB="Multiple"
            aria-label="Multiple"
            toggled={isInMultipleMode}
            onToggle={() => {
              setIsInMultipleMode(!isInMultipleMode);
            }}
          />
        </VariableValueContainer>
      </Stack>

      {isInMultipleMode
        ? createPortal(
            <MultipleValuesModal
              isVisible={isModalVisible}
              initialValue={formState.values?.variableValues}
              onClose={() => {
                setIsModalVisible(false);
                tracking.track({
                  eventName: 'json-editor-closed',
                  variant: 'search-multiple-variables',
                });
              }}
              onApply={(value) => {
                form.change('variableValues', value);
                setIsModalVisible(false);
                tracking.track({
                  eventName: 'json-editor-saved',
                  variant: 'search-multiple-variables',
                });
              }}
            />,
            document.body,
          )
        : createPortal(
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
