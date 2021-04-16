/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ValidationErrors} from 'final-form';

const getError = (errors: ValidationErrors): string | void => {
  const nameFieldError = errors?.name;
  const valueFieldError = errors?.value;

  if (nameFieldError === undefined && valueFieldError === undefined) {
    return;
  }

  if (nameFieldError !== undefined && valueFieldError !== undefined) {
    return `${nameFieldError} and ${valueFieldError}`;
  }

  return nameFieldError ?? valueFieldError;
};

export {getError};
