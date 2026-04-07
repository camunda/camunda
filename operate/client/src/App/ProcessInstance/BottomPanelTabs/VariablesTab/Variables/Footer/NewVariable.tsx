/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Field, useForm, useFormState} from 'react-final-form';
import {useFieldError} from 'modules/hooks/useFieldError';
import {Layer} from './styled';
import {
  validateNameCharacters,
  validateValueValid,
  validateValueComplete,
  validateNameComplete,
  validateNameNotDuplicate,
} from '../validators';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {tracking} from 'modules/tracking';
import {TextInputField} from 'modules/components/TextInputField';
import {Operations} from '../Operations';
import {EditButtons} from '../EditButtons';
import {useVariables} from 'modules/queries/variables/useVariables';
import {MaximizeButton} from '../ViewFullVariableButton/MaximizeButton';
import {InlineJsonEditor} from 'modules/components/InlineJsonEditor';

const NewVariable: React.FC = () => {
  const formState = useFormState();
  const form = useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const {data: variablesData} = useVariables();
  const allVariables =
    variablesData?.pages.flatMap((page) => (page.items ? page.items : [])) ??
    [];
  const valueError = useFieldError('value');

  return (
    <>
      <Layer>
        <Field
          name="name"
          validate={mergeValidators(
            validateNameCharacters,
            validateNameComplete(allVariables),
            validateNameNotDuplicate(allVariables),
          )}
          allowNull={false}
          parse={(value) => value}
        >
          {({input}) => (
            <TextInputField
              {...input}
              id="name"
              size="sm"
              hideLabel
              labelText="Name"
              type="text"
              placeholder="Name"
              autoFocus={true}
            />
          )}
        </Field>
        <Field
          name="value"
          validate={mergeValidators(validateValueComplete, validateValueValid)}
          parse={(value) => value}
        >
          {({input}) => (
            <InlineJsonEditor
              {...input}
              label="Value"
              id="value"
              fieldError={valueError}
            />
          )}
        </Field>
        <Operations>
          <MaximizeButton
            onClick={() => {
              setIsModalVisible(true);
              tracking.track({
                eventName: 'json-editor-opened',
                variant: 'add-variable',
              });
            }}
          />
          <EditButtons />
        </Operations>
      </Layer>
      {isModalVisible && (
        <JSONEditorModal
          isVisible={isModalVisible}
          title={
            formState.values?.name
              ? `Edit Variable "${formState.values?.name}"`
              : 'Edit a new Variable'
          }
          value={formState.values?.value}
          onClose={() => {
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-closed',
              variant: 'add-variable',
            });
          }}
          onApply={(value) => {
            form.change('value', value);
            form.submit();
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-saved',
              variant: 'add-variable',
            });
          }}
        />
      )}
    </>
  );
};

export {NewVariable};
