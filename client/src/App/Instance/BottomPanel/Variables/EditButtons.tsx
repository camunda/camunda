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

const EditButtons: React.FC = () => {
  const form = useForm();
  const {values, initialValues, errors, submitErrors, dirtySinceLastSubmit} =
    useFormState();

  const errorMessage = getError(
    errors,
    dirtySinceLastSubmit ? [] : submitErrors
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
          initialValues.value === values.value || errorMessage !== undefined
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
