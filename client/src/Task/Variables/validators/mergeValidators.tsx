/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FieldValidator} from 'final-form';

const isPromise = (value: any) => {
  return Boolean(value && typeof value.then === 'function');
};

const mergeValidators = (
  ...validators: Array<FieldValidator<string | undefined>>
) => {
  return (
    ...validateParams: Parameters<FieldValidator<string | undefined>>
  ) => {
    const executedValidators = validators.map((validator) =>
      validator(...validateParams),
    );
    const syncValidators = executedValidators.filter(
      (validator) => !isPromise(validator),
    );
    const asyncValidators = executedValidators.filter((validator) =>
      isPromise(validator),
    );

    const immediateError = syncValidators.reduce(
      (error, result) => error ?? result,
      undefined,
    );

    if (immediateError !== undefined) {
      return immediateError;
    }

    if (asyncValidators.length === 0) {
      return undefined;
    }

    return new Promise((resolve) => {
      asyncValidators.forEach((result) => resolve(result));
    });
  };
};

export {mergeValidators};
