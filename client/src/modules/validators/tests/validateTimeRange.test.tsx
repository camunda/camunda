/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {validateTimeRange} from '../index';
import {mockMeta} from './mocks';

jest.unmock('modules/utils/date/formatDate');

const TIME_RANGE_ERROR = '"From time" is after "To time"';

const FROM_TIME_META = {
  ...mockMeta,
  name: 'fromTime',
};
const TO_TIME_META = {
  ...mockMeta,
  name: 'toTime',
};

describe('should validate time range', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });
  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('with one of the date/time fields undefined', () => {
    expect(
      validateTimeRange('12:00:00', {fromTime: '12:00:00'}, FROM_TIME_META)
    ).toBe(undefined);
    expect(
      validateTimeRange(
        '12:00:00',
        {fromTime: '12:00:00', toTime: '10:00:00'},
        FROM_TIME_META
      )
    ).toBe(undefined);
    expect(
      validateTimeRange(
        '12:00:00',
        {fromTime: '12:00:00', toTime: '10:00:00', fromDate: '2023-03-29'},
        FROM_TIME_META
      )
    ).toBe(undefined);
  });

  it('with different days and invalid time range', () => {
    expect(
      validateTimeRange(
        '12:12:12',
        {
          fromDate: '2023-03-27',
          toDate: '2023-03-28',
          fromTime: '12:12:12',
          toTime: '11:11:11',
        },
        FROM_TIME_META
      )
    ).toBe(undefined);
    expect(
      validateTimeRange(
        '11:11:11',
        {
          fromDate: '2023-03-27',
          toDate: '2023-03-28',
          fromTime: '12:12:12',
          toTime: '11:11:11',
        },
        TO_TIME_META
      )
    ).toBe(undefined);
  });

  it('with same day and invalid time range', () => {
    expect(
      validateTimeRange(
        '12:12:12',
        {
          fromDate: '2023-03-27',
          toDate: '2023-03-27',
          fromTime: '12:12:12',
          toTime: '11:11:11',
        },
        FROM_TIME_META
      )
    ).resolves.toBe(TIME_RANGE_ERROR);

    expect(
      validateTimeRange(
        '11:11:11',
        {
          fromDate: '2023-03-27',
          toDate: '2023-03-27',
          fromTime: '12:12:12',
          toTime: '11:11:11',
        },
        TO_TIME_META
      )
    ).resolves.toBe(' ');
  });
  it('with same day and valid time range', () => {
    expect(
      validateTimeRange(
        '11:11:11',
        {
          fromDate: '2023-03-27',
          toDate: '2023-03-27',
          fromTime: '11:11:11',
          toTime: '12:12:12',
        },
        FROM_TIME_META
      )
    ).toBe(undefined);

    expect(
      validateTimeRange(
        '12:12:12',
        {
          fromDate: '2023-03-27',
          toDate: '2023-03-27',
          fromTime: '11:11:11',
          toTime: '12:12:12',
        },
        TO_TIME_META
      )
    ).toBe(undefined);
  });
});
