/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {formatDate} from './formatDate';

jest.unmock('modules/utils/date/formatDate');

describe('formatDate', () => {
  it('should return -- if date is null', () => {
    expect(formatDate(null)).toBe('--');
  });

  it('should return formatted date (Date object)', () => {
    // given
    const givenDate = new Date('2019-11-06T14:24:15.422');
    const expectedDate = '2019-11-06 14:24:15';

    // then
    expect(formatDate(givenDate)).toEqual(expectedDate);
  });

  it('should return formatted date (Date string)', () => {
    // given
    const givenDate = '2019-11-06T14:24:15.422';
    const expectedDate = '2019-11-06 14:24:15';

    // then
    expect(formatDate(givenDate)).toEqual(expectedDate);
  });
});
