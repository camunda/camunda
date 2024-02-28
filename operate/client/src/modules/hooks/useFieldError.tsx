/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useField, useFormState} from 'react-final-form';
import {useEffect, useState} from 'react';
import get from 'lodash/get';

const useFieldError = (name: string) => {
  const {
    input: {value},
    meta: {active, dirtySinceLastSubmit, validating},
  } = useField(name);

  const {errors, submitErrors, validating: formValidating} = useFormState();

  const error = get(errors, name);
  const submitError = dirtySinceLastSubmit
    ? undefined
    : get(submitErrors, name);

  const [computedError, setComputedError] = useState<string | undefined>();

  useEffect(() => {
    if ((formValidating || validating) && !active) {
      return;
    }

    setComputedError(error ?? submitError);
  }, [
    formValidating,
    validating,
    active,
    error,
    submitError,
    setComputedError,
    value,
  ]);

  return computedError;
};
export {useFieldError};
