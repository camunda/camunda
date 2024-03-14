/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
      validateTimeRange('12:00:00', {fromTime: '12:00:00'}, FROM_TIME_META),
    ).toBe(undefined);
    expect(
      validateTimeRange(
        '12:00:00',
        {fromTime: '12:00:00', toTime: '10:00:00'},
        FROM_TIME_META,
      ),
    ).toBe(undefined);
    expect(
      validateTimeRange(
        '12:00:00',
        {fromTime: '12:00:00', toTime: '10:00:00', fromDate: '2023-03-29'},
        FROM_TIME_META,
      ),
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
        FROM_TIME_META,
      ),
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
        TO_TIME_META,
      ),
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
        FROM_TIME_META,
      ),
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
        TO_TIME_META,
      ),
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
        FROM_TIME_META,
      ),
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
        TO_TIME_META,
      ),
    ).toBe(undefined);
  });
});
