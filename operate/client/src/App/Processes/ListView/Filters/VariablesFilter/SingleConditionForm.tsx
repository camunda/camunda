/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Stack, TextInput} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {useNavigate, useLocation} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {Field, Form, useForm} from 'react-final-form';
import type {FieldValidator} from 'final-form';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {Paths} from 'modules/Routes';
import {smartTransformValue} from 'modules/utils/smartTransform';
import {AutoSubmit} from 'modules/components/AutoSubmit';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {InlineButtonRow} from './styled';

const VALIDATION_TIMEOUT = 750;

type FormValues = {
  name?: string;
  value?: string;
};

const validateNameComplete: FieldValidator<string | undefined> =
  promisifyValidator((name = '', allValues) => {
    const value = (allValues as FormValues | undefined)?.value?.trim() ?? '';
    const trimmedName = name.trim();
    if ((trimmedName === '' && value === '') || trimmedName !== '') {
      return undefined;
    }
    return 'Name has to be filled';
  }, VALIDATION_TIMEOUT);

const validateValueComplete: FieldValidator<string | undefined> =
  promisifyValidator((value = '', allValues) => {
    const name = (allValues as FormValues | undefined)?.name?.trim() ?? '';
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

const sameAsStored = (values: FormValues): boolean => {
  const current = variableFilterStore.conditions[0];
  if (!current || variableFilterStore.conditions.length !== 1) {
    return false;
  }
  return (
    current.name === (values.name ?? '').trim() &&
    current.operator === 'equals' &&
    current.value === (values.value ?? '')
  );
};

const InlineForm: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const form = useForm<FormValues>();

  const openModal = async () => {
    await form.submit();
    navigate({
      pathname: Paths.processesVariables(),
      search: location.search,
    });
  };

  return (
    <Stack gap={3}>
      <AutoSubmit />
      <Field<string | undefined>
        name="name"
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
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                form.submit();
              }
            }}
            invalid={!meta.dirtySinceLastSubmit && meta.error !== undefined}
            invalidText={meta.dirtySinceLastSubmit ? undefined : meta.error}
            autoComplete="off"
            data-testid="single-condition-name"
          />
        )}
      </Field>
      <Field<string | undefined>
        name="value"
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
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                form.submit();
              }
            }}
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
};

const SingleConditionForm: React.FC = observer(() => {
  const seed = variableFilterStore.conditions[0];
  const initialValues: FormValues = {
    name: seed?.name ?? '',
    value: seed?.value ?? '',
  };

  const handleSubmit = (values: FormValues) => {
    const trimmedName = (values.name ?? '').trim();
    const rawValue = values.value ?? '';
    const bothEmpty = !trimmedName && rawValue.trim() === '';

    if (bothEmpty) {
      if (variableFilterStore.conditions.length > 0) {
        variableFilterStore.setConditions([]);
      }
      return;
    }
    if (sameAsStored(values)) {
      return;
    }

    variableFilterStore.setConditions([
      {name: trimmedName, operator: 'equals', value: rawValue},
    ]);
  };

  return (
    <Form<FormValues> onSubmit={handleSubmit} initialValues={initialValues}>
      {() => <InlineForm />}
    </Form>
  );
});

export {SingleConditionForm};
