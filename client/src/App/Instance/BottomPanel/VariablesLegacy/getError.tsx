/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const getError = (
  nameFieldError?: string,
  valueFieldError?: string
): string | undefined => {
  if (nameFieldError === undefined && valueFieldError === undefined) {
    return;
  }

  if (nameFieldError !== undefined && valueFieldError !== undefined) {
    return `${nameFieldError} and ${valueFieldError}`;
  }

  return nameFieldError ?? valueFieldError;
};

export {getError};
