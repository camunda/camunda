/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Container, CloseIcon, CheckIcon} from './styled';
import {useForm, useFormState} from 'react-final-form';
import {getError} from '../getError';
import {useFieldError} from 'modules/hooks/useFieldError';
import {ActionButton} from 'modules/components/ActionButton';

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
    <Container>
      <ActionButton
        title="Exit edit mode"
        onClick={() => form.reset({})}
        icon={<CloseIcon />}
      />
      <ActionButton
        title="Save variable"
        isDisabled={
          initialValues.value === values.value ||
          validating ||
          hasValidationErrors ||
          errorMessage !== undefined
        }
        onClick={() => form.submit()}
        icon={<CheckIcon />}
      />
    </Container>
  );
};

export {EditButtons};
