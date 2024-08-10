/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatDate} from './formatDate';

jest.unmock('modules/utils/date/formatDate');

describe('formatDate', () => {
  it('should return default placeholder if date is null', () => {
    expect(formatDate(null)).toBe('--');
  });

  it('should return custom placeholder, if date is null', () => {
    const givenDate = null;
    const expectedDate = '???';

    expect(formatDate(givenDate, '???')).toEqual(expectedDate);
  });

  it('should return formatted date (Date object)', () => {
    const givenDate = new Date('2019-11-06T14:24:15.422');
    const expectedDate = '2019-11-06 14:24:15';

    expect(formatDate(givenDate)).toEqual(expectedDate);
  });

  it('should return formatted date (Date string)', () => {
    const givenDate = '2019-11-06T14:24:15.422';
    const expectedDate = '2019-11-06 14:24:15';

    expect(formatDate(givenDate)).toEqual(expectedDate);
  });

  it('should return formatted date (Date object, custom placeholder)', () => {
    const givenDate = new Date('2019-11-06T14:24:15.422');
    const expectedDate = '2019-11-06 14:24:15';

    expect(formatDate(givenDate, null)).toEqual(expectedDate);
  });
});
