/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getRange} from './service';

describe('ListFooter/service', () => {
  describe('getRange', () => {
    it('should behave correctly on the first pages', () => {
      expect(getRange(1, 10)).toEqual([1, 2, 3, 4, 5]);
      expect(getRange(2, 10)).toEqual([1, 2, 3, 4, 5]);
      expect(getRange(3, 10)).toEqual([1, 2, 3, 4, 5]);
    });

    it('should behave correctly on the last pages', () => {
      expect(getRange(8, 10)).toEqual([6, 7, 8, 9, 10]);
      expect(getRange(9, 10)).toEqual([6, 7, 8, 9, 10]);
      expect(getRange(10, 10)).toEqual([6, 7, 8, 9, 10]);
    });

    it('should behave correctly if there are few pages', () => {
      expect(getRange(1, 1)).toEqual([1]);
      expect(getRange(1, 2)).toEqual([1, 2]);
      expect(getRange(2, 4)).toEqual([1, 2, 3, 4]);
    });

    it('should behave correctly if there are a lot of pages', () => {
      expect(getRange(27, 316)).toEqual([25, 26, 27, 28, 29]);
      expect(getRange(13, 18)).toEqual([11, 12, 13, 14, 15]);
    });
  });
});
