/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  Warning,
  EditButton,
  CloseIcon,
  CheckIcon,
  EditButtonsContainer,
} from './styled';
import {useForm, useFormState} from 'react-final-form';
import {getError} from './getError';
import {Warning as WarningIcon} from 'modules/components/Warning';

import {useFieldError} from 'modules/hooks/useFieldError';

const EditButtons: React.FC = () => {
  const form = useForm();
  const {values, initialValues, validating, hasValidationErrors} =
    useFormState();

  const nameError = useFieldError('name');
  const valueError = useFieldError('value');
  const errorMessage = getError(
    initialValues.name === '' ? nameError : undefined,
    valueError
  );

  return (
    <EditButtonsContainer>
      <Warning>
        {errorMessage !== undefined && <WarningIcon title={errorMessage} />}
      </Warning>

      <EditButton
        type="button"
        title="Exit edit mode"
        onClick={() => form.reset({})}
        size="large"
        iconButtonTheme="default"
        icon={<CloseIcon />}
      />

      <EditButton
        type="button"
        title="Save variable"
        disabled={
          initialValues.value === values.value ||
          validating ||
          hasValidationErrors ||
          errorMessage !== undefined
        }
        onClick={() => form.submit()}
        size="large"
        iconButtonTheme="default"
        icon={<CheckIcon />}
      />
    </EditButtonsContainer>
  );
};

export {EditButtons};
