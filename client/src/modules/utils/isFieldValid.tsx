/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FieldMetaState} from 'react-final-form';

const isFieldValid = (
  meta: FieldMetaState<string | undefined>,
  submitError?: string
) => {
  const {dirtySinceLastSubmit, error} = meta;

  return (
    typeof error !== 'string' &&
    (dirtySinceLastSubmit ||
      (submitError === undefined && meta.submitError === undefined))
  );
};

export {isFieldValid};
