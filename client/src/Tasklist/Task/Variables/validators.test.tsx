/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {validateJSON, validateNonEmpty} from './validators';

describe('Validators', () => {
  describe('validateJSON', () => {
    it('should validate', () => {
      ['"abc"', '123', 'true', '{"name": "value"}', '[1, 2, 3]'].forEach(
        (value) => {
          expect(validateJSON(value)).toBeUndefined();
        },
      );
    });

    it('should not validate', () => {
      [undefined, 'abc', '"abc', '{name: "value"}', '[[0]', '() => {}'].forEach(
        (value) => {
          expect(validateJSON(value)).toBe('Value has to be JSON');
        },
      );
    });
  });

  describe('validateNonEmpty', () => {
    it('should validate', () => {
      ['abc', 'true', '123'].forEach((value) => {
        expect(validateNonEmpty(value)).toBeUndefined();
      });
    });

    it('should not validate', () => {
      [undefined, '', ' ', '           '].forEach((value) => {
        expect(validateNonEmpty(value)).toBe('Name has to be filled');
      });
    });
  });
});
