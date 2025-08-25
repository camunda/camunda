/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useForm, useFormState} from 'react-final-form';
import {getError} from './getError';
import {useFieldError} from 'modules/hooks/useFieldError';
import {Button} from '@carbon/react';
import {Checkmark, Close} from '@carbon/react/icons';
import {Loading} from './EditButtons.styled';

const EditButtons: React.FC = () => {
  const form = useForm();
  const {values, initialValues, validating, hasValidationErrors} =
    useFormState();

  const nameError = useFieldError('name');
  const valueError = useFieldError('value');
  const errorMessage = getError(
    initialValues.name === '' ? nameError : undefined,
    valueError,
  );

  return (
    <>
      <Button
        kind="ghost"
        size="sm"
        iconDescription="Exit edit mode"
        aria-label="Exit edit mode"
        tooltipPosition="left"
        onClick={() => {
          form.reset({});
        }}
        hasIconOnly
        renderIcon={Close}
        disabled={form.getState().submitting}
      />

      {form.getState().submitting ? (
        <Loading small withOverlay={false} data-testid="full-variable-loader" />
      ) : (
        <Button
          kind="ghost"
          size="sm"
          iconDescription="Save variable"
          aria-label="Save variable"
          tooltipPosition="left"
          disabled={
            initialValues.value === values.value ||
            validating ||
            hasValidationErrors ||
            errorMessage !== undefined
          }
          onClick={() => form.submit()}
          hasIconOnly
          renderIcon={Checkmark}
        />
      )}
    </>
  );
};

export {EditButtons};
