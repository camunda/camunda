/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const VALIDATION_DELAY = 1000;
const ERRORS = {
  EMPTY_NAME: 'Name has to be filled',
  INVALID_NAME: 'Name is invalid',
  DUPLICATE_NAME: 'Name should be unique',
  INVALID_VALUE: 'Value has to be JSON',
  EMPTY_VALUE: 'Value has to be filled',
} as const;

export {VALIDATION_DELAY, ERRORS};
