/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
