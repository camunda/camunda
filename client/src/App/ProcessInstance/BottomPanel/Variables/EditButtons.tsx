/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useForm, useFormState} from 'react-final-form';
import {getError} from './getError';
import {useFieldError} from 'modules/hooks/useFieldError';
import {Button} from '@carbon/react';
import {Checkmark, Close} from '@carbon/react/icons';

type Props = {
  onExitEditMode?: () => void;
};

const EditButtons: React.FC<Props> = ({onExitEditMode}) => {
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
          onExitEditMode?.();
          form.reset({});
        }}
        hasIconOnly
        renderIcon={Close}
      />

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
    </>
  );
};

export {EditButtons};
