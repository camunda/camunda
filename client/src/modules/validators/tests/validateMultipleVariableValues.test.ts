/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
