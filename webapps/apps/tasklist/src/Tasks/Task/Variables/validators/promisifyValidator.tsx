/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FieldValidator} from 'final-form';

const promisifyValidator = (
  validator: FieldValidator<string | undefined>,
  debounceTimeout: number,
) => {
  return (...params: Parameters<FieldValidator<string | undefined>>) => {
    const errorMessage = validator(...params);
    if (errorMessage === undefined) {
      return undefined;
    }

    const [, , meta] = params;

    if (!meta?.active && meta?.error === errorMessage) {
      return errorMessage;
    }

    return new Promise((resolve) => {
      setTimeout(() => {
        resolve(errorMessage);
      }, debounceTimeout);
    });
  };
};

export {promisifyValidator};
