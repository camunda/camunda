/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const getError = (
  nameFieldError?: string,
  valueFieldError?: string,
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
