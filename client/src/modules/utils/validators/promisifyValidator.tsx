/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FieldValidator} from 'final-form';

const promisifyValidator = (
  validator: FieldValidator<string | undefined>,
  debounceTimeout: number
) => {
  return (...params: Parameters<FieldValidator<string | undefined>>) => {
    const errorMessage = validator(...params);
    if (errorMessage === undefined) {
      return undefined;
    }

    return new Promise((resolve) => {
      setTimeout(() => {
        resolve(errorMessage);
      }, debounceTimeout);
    });
  };
};

export {promisifyValidator};
