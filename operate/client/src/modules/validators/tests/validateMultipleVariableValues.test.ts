/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {validateMultipleVariableValues} from '../validateMultipleVariableValues';

describe('validateMultipleVariableValues', () => {
  it.each(['invalid', 'invalid,invalid', ',', ' ', '{'])(
    'should return false for %p',
    (input) => {
      expect(validateMultipleVariableValues(input)).toBe(false);
    },
  );

  it.each(['"valid"', '1,2', '[1,2]', '"valid", "and valid"', ''])(
    'should return true for %p',
    (input) => {
      expect(validateMultipleVariableValues(input)).toBe(true);
    },
  );
});
