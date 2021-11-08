/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const VALIDATION_DELAY = 750;
const ERRORS = {
  EMPTY_NAME: 'Name has to be filled',
  INVALID_NAME: 'Name is invalid',
  DUPLICATE_NAME: 'Name should be unique',
  INVALID_VALUE: 'Value has to be JSON',
} as const;

export {VALIDATION_DELAY, ERRORS};
