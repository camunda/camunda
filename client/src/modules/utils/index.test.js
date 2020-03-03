/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {tryDecodeURI, tryDecodeURIComponent} from './index';

describe('modules/utils/index.js', () => {
  describe('tryDecodeURI and tryDecodeURIComponent', () => {
    it('should return same text if fails to decode', () => {
      const invalidInput = '&';
      expect(tryDecodeURI(invalidInput)).toEqual(invalidInput);
      expect(tryDecodeURIComponent(invalidInput)).toEqual(invalidInput);
    });

    it('should decode text', () => {
      const cases = [
        {encoded: '%25', decoded: '%'},
        {encoded: '%20', decoded: ' '}
      ];

      cases.forEach(({encoded, decoded}) => {
        expect(tryDecodeURI(encoded)).toEqual(decoded);
        expect(tryDecodeURIComponent(encoded)).toEqual(decoded);
      });
    });
  });
});
