/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {Button, Stack, TextInput} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {useNavigate, useLocation} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {Field, useForm, useFormState} from 'react-final-form';
import type {FieldValidator} from 'final-form';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {Paths} from 'modules/Routes';
import {smartTransformValue} from 'modules/utils/smartTransform';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {InlineButtonRow} from './styled';

const VALIDATION_TIMEOUT = 750;

type AllValues = {
  variableName?: string;
  variableValues?: string;
};

const validateNameComplete: FieldValidator<string | undefined> =
  promisifyValidator((name = '', allValues) => {
    const value =
      (allValues as AllValues | undefined)?.variableValues?.trim() ?? '';
    const trimmedName = name.trim();
    if ((trimmedName === '' && value === '') || trimmedName !== '') {
      return undefined;
    }
    return 'Name has to be filled';
  }, VALIDATION_TIMEOUT);

const validateValueComplete: FieldValidator<string | undefined> =
  promisifyValidator((value = '', allValues) => {
    const name =
      (allValues as AllValues | undefined)?.variableName?.trim() ?? '';
    const trimmedValue = value.trim();
    if ((name === '' && trimmedValue === '') || trimmedValue !== '') {
      return undefined;
    }
    return 'Value has to be filled';
  }, VALIDATION_TIMEOUT);

const validateValueParseable: FieldValidator<string | undefined> =
  promisifyValidator((value = '') => {
    if (value.trim() === '') {
      return undefined;
    }
    try {
      smartTransformValue(value);
      return undefined;
    } catch (e) {
      return e instanceof Error ? e.message : 'Invalid value';
    }
  }, VALIDATION_TIMEOUT);

const SingleConditionForm: React.FC = observer(() => {
  const navigate = useNavigate();
  const location = useLocation();
  const form = useForm();
  const {conditions} = variableFilterStore;
  const seed = conditions[0];

  const lastSyncRef = useRef({name: '', value: ''});
  useEffect(() => {
    const seedName = seed?.name ?? '';
    const seedValue = seed?.value ?? '';
    if (
      seedName === lastSyncRef.current.name &&
      seedValue === lastSyncRef.current.value
    ) {
      return;
    }
    lastSyncRef.current = {name: seedName, value: seedValue};
    form.batch(() => {
      form.change('variableName', seedName);
      form.change('variableValues', seedValue);
    });
  }, [seed?.name, seed?.value, form]);

  const {values, errors, validating} = useFormState<AllValues>({
    subscription: {values: true, errors: true, validating: true},
  });
  const rawName = values?.['variableName'] ?? '';
  const rawValue = values?.['variableValues'] ?? '';
  const nameError = errors?.['variableName'];
  const valueError = errors?.['variableValues'];

  useEffect(() => {
    if (validating) {
      return;
    }
    const trimmedName = rawName.trim();
    const bothEmpty = !trimmedName && rawValue.trim() === '';

    if (bothEmpty) {
      if (variableFilterStore.conditions.length > 0) {
        lastSyncRef.current = {name: '', value: ''};
        variableFilterStore.setConditions([]);
      }
      return;
    }

    if (nameError !== undefined || valueError !== undefined) {
      return;
    }

    const current = variableFilterStore.conditions[0];
    if (
      current?.name === trimmedName &&
      current?.operator === 'equals' &&
      current?.value === rawValue &&
      variableFilterStore.conditions.length === 1
    ) {
      return;
    }
    lastSyncRef.current = {name: trimmedName, value: rawValue};
    variableFilterStore.setConditions([
      {name: trimmedName, operator: 'equals', value: rawValue},
    ]);
  }, [rawName, rawValue, nameError, valueError, validating]);

  const openModal = () => {
    const state = form.getState();
    const currentValues = state.values as AllValues | undefined;
    const rawName = (currentValues?.variableName ?? '').trim();
    const rawValue = currentValues?.variableValues ?? '';
    if (rawName || rawValue.trim()) {
      const current = variableFilterStore.conditions[0];
      const same =
        current?.name === rawName &&
        current?.operator === 'equals' &&
        current?.value === rawValue &&
        variableFilterStore.conditions.length === 1;
      if (!same) {
        lastSyncRef.current = {name: rawName, value: rawValue};
        variableFilterStore.setConditions([
          {name: rawName, operator: 'equals', value: rawValue},
        ]);
      }
    }
    navigate({
      pathname: Paths.processesVariables(),
      search: location.search,
    });
  };

  return (
    <Stack gap={3}>
      <Field<string | undefined>
        name="variableName"
        validate={validateNameComplete}
        subscription={{
          value: true,
          error: true,
          dirtySinceLastSubmit: true,
        }}
      >
        {({input, meta}) => (
          <TextInput
            id="single-condition-name"
            labelText="Name"
            size="sm"
            placeholder="Variable name"
            value={input.value}
            onChange={input.onChange}
            onBlur={input.onBlur}
            invalid={!meta.dirtySinceLastSubmit && meta.error !== undefined}
            invalidText={meta.dirtySinceLastSubmit ? undefined : meta.error}
            autoComplete="off"
            data-testid="single-condition-name"
          />
        )}
      </Field>
      <Field<string | undefined>
        name="variableValues"
        validate={mergeValidators(
          validateValueComplete,
          validateValueParseable,
        )}
        subscription={{
          value: true,
          error: true,
          dirtySinceLastSubmit: true,
        }}
      >
        {({input, meta}) => (
          <TextInput
            id="single-condition-value"
            labelText="Value"
            size="sm"
            placeholder="e.g. true, 42, hello"
            value={input.value}
            onChange={input.onChange}
            onBlur={input.onBlur}
            invalid={!meta.dirtySinceLastSubmit && meta.error !== undefined}
            invalidText={meta.dirtySinceLastSubmit ? undefined : meta.error}
            autoComplete="off"
            data-testid="single-condition-value"
          />
        )}
      </Field>
      <InlineButtonRow>
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Add}
          onClick={openModal}
          data-testid="open-variable-filter-modal"
        >
          Add condition
        </Button>
      </InlineButtonRow>
    </Stack>
  );
});

export {SingleConditionForm};
