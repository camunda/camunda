/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatDate} from './date';

describe('date utils', () => {
  describe('formatDate', () => {
    it('should return -- if date is null', () => {
      expect(formatDate(null)).toBe('--');
    });

    it('should return formatted date', () => {
      // given
      const givenDate = new Date('Thu Jun 28 2018 13:59:05');
      const expectedDate = '2018-06-28 13:59:05';

      // then
      expect(formatDate(givenDate)).toEqual(expectedDate);
    });
  });
});
