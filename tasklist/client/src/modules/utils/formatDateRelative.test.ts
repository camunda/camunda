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

import {add, setDefaultOptions, sub} from 'date-fns';
import {enUS} from 'date-fns/locale';
import {formatDate, formatDateTime} from './formatDateRelative';

const now = new Date(2024, 0, 10, 12, 0, 0, 0);

describe('format', () => {
  beforeAll(() => {
    setDefaultOptions({locale: enUS});
  });
  afterAll(() => {
    setDefaultOptions({});
  });

  describe('formatDate', () => {
    it('shows now when date is the same', () => {
      expect(formatDate(now, now)).to.be.toEqual({
        date: new Date(Date.parse('2024-01-10T12:00:00Z')),
        relative: {
          resolution: 'now',
          text: 'Now',
          speech: 'Now',
        },
        absolute: {text: '10 Jan 2024'},
      });
    });
    it('shows minutes when date is within one hour', () => {
      expect(formatDate(sub(now, {minutes: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T11:59:00Z')),
        relative: {
          resolution: 'minutes',
          text: '1 minute ago',
          speech: '1 minute ago',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(sub(now, {minutes: 10}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T11:50:00Z')),
        relative: {
          resolution: 'minutes',
          text: '10 minutes ago',
          speech: '10 minutes ago',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(sub(now, {minutes: 60}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T11:00:00Z')),
        relative: {
          resolution: 'minutes',
          text: '60 minutes ago',
          speech: '60 minutes ago',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(sub(now, {minutes: 61}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-10T10:59:00Z')),
        relative: {
          resolution: 'minutes',
          text: '61 minutes ago',
          speech: '61 minutes ago',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(add(now, {minutes: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T12:01:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 1 minute',
          speech: 'In 1 minute',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(add(now, {minutes: 10}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T12:10:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 10 minutes',
          speech: 'In 10 minutes',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(add(now, {minutes: 60}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T13:00:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 60 minutes',
          speech: 'In 60 minutes',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(add(now, {minutes: 61}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-10T13:01:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 61 minutes',
          speech: 'In 61 minutes',
        },
        absolute: {text: '10 Jan 2024'},
      });
    });
    it('shows Today when day is the same', () => {
      expect(formatDate(sub(now, {minutes: 61}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T10:59:00Z')),
        relative: {
          resolution: 'days',
          text: 'Today',
          speech: 'Today',
        },
        absolute: {text: '10 Jan 2024'},
      });
      expect(formatDate(add(now, {minutes: 61}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T13:01:00Z')),
        relative: {
          resolution: 'days',
          text: 'Today',
          speech: 'Today',
        },
        absolute: {text: '10 Jan 2024'},
      });
    });
    it('shows Tomorrow when day is the next day', () => {
      expect(formatDate(add(now, {days: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-11T12:00:00Z')),
        relative: {
          resolution: 'days',
          text: 'Tomorrow',
          speech: 'Tomorrow',
        },
        absolute: {text: '11 Jan 2024'},
      });
    });
    it('shows Yesterday when day is the previous day', () => {
      expect(formatDate(sub(now, {days: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-09T12:00:00Z')),
        relative: {
          resolution: 'days',
          text: 'Yesterday',
          speech: 'Yesterday',
        },
        absolute: {text: '9 Jan 2024'},
      });
    });
    it('shows day of week when day is beyond the previous or next day', () => {
      expect(formatDate(sub(now, {days: 4}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-06T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Saturday',
          speech: 'Saturday',
        },
        absolute: {text: '6 Jan 2024'},
      });
      expect(formatDate(sub(now, {days: 3}), now)).toEqual({
        date: new Date(Date.parse('2024-01-07T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Sunday',
          speech: 'Sunday',
        },
        absolute: {text: '7 Jan 2024'},
      });
      expect(formatDate(sub(now, {days: 2}), now)).toEqual({
        date: new Date(Date.parse('2024-01-08T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Monday',
          speech: 'Monday',
        },
        absolute: {text: '8 Jan 2024'},
      });
      expect(formatDate(add(now, {days: 2}), now)).toEqual({
        date: new Date(Date.parse('2024-01-12T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Friday',
          speech: 'Friday',
        },
        absolute: {text: '12 Jan 2024'},
      });
      expect(formatDate(add(now, {days: 3}), now)).toEqual({
        date: new Date(Date.parse('2024-01-13T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Saturday',
          speech: 'Saturday',
        },
        absolute: {text: '13 Jan 2024'},
      });
      expect(formatDate(add(now, {days: 4}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-14T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Sunday',
          speech: 'Sunday',
        },
        absolute: {text: '14 Jan 2024'},
      });
    });
    it('shows date when day is beyond the current week', () => {
      expect(formatDate(sub(now, {days: 4}), now)).toEqual({
        date: new Date(Date.parse('2024-01-06T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '6 Jan',
          speech: '6th of January',
        },
        absolute: {text: '6 Jan 2024'},
      });
      expect(formatDate(add(now, {days: 4}), now)).toEqual({
        date: new Date(Date.parse('2024-01-14T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '14 Jan',
          speech: '14th of January',
        },
        absolute: {text: '14 Jan 2024'},
      });
    });
    it('shows date and year when day is beyond the current week', () => {
      expect(formatDate(sub(now, {days: 10}), now)).toEqual({
        date: new Date(Date.parse('2023-12-31T12:00:00Z')),
        relative: {
          resolution: 'years',
          text: '31 Dec 2023',
          speech: '31st of December, 2023',
        },
        absolute: {text: '31 Dec 2023'},
      });
      expect(formatDate(sub(now, {days: 9}), now)).toEqual({
        date: new Date(Date.parse('2024-01-01T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '1 Jan',
          speech: '1st of January',
        },
        absolute: {text: '1 Jan 2024'},
      });
      expect(formatDate(add(now, {days: 356}), now)).toEqual({
        date: new Date(Date.parse('2024-12-31T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '31 Dec',
          speech: '31st of December',
        },
        absolute: {text: '31 Dec 2024'},
      });
      expect(formatDate(add(now, {days: 357}), now)).toEqual({
        date: new Date(Date.parse('2025-01-01T12:00:00Z')),
        relative: {
          resolution: 'years',
          text: '1 Jan 2025',
          speech: '1st of January, 2025',
        },
        absolute: {text: '1 Jan 2025'},
      });
    });
  });
  describe('formatDateTime', () => {
    it('shows now when date is the same', () => {
      expect(formatDateTime(now, now)).to.be.toEqual({
        date: new Date(Date.parse('2024-01-10T12:00:00Z')),
        relative: {
          resolution: 'now',
          text: 'Now',
          speech: 'Now',
        },
        absolute: {text: '10 Jan 2024, 12:00'},
      });
    });
    it('shows minutes when date is within one hour', () => {
      expect(formatDateTime(sub(now, {minutes: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T11:59:00Z')),
        relative: {
          resolution: 'minutes',
          text: '1 minute ago',
          speech: '1 minute ago',
        },
        absolute: {text: '10 Jan 2024, 11:59'},
      });
      expect(formatDateTime(sub(now, {minutes: 10}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T11:50:00Z')),
        relative: {
          resolution: 'minutes',
          text: '10 minutes ago',
          speech: '10 minutes ago',
        },
        absolute: {text: '10 Jan 2024, 11:50'},
      });
      expect(formatDateTime(sub(now, {minutes: 60}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T11:00:00Z')),
        relative: {
          resolution: 'minutes',
          text: '60 minutes ago',
          speech: '60 minutes ago',
        },
        absolute: {text: '10 Jan 2024, 11:00'},
      });
      expect(formatDateTime(sub(now, {minutes: 61}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-10T10:59:00Z')),
        relative: {
          resolution: 'minutes',
          text: '61 minutes ago',
          speech: '61 minutes ago',
        },
        absolute: {text: '10 Jan 2024, 10:59'},
      });
      expect(formatDateTime(add(now, {minutes: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T12:01:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 1 minute',
          speech: 'In 1 minute',
        },
        absolute: {text: '10 Jan 2024, 12:01'},
      });
      expect(formatDateTime(add(now, {minutes: 10}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T12:10:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 10 minutes',
          speech: 'In 10 minutes',
        },
        absolute: {text: '10 Jan 2024, 12:10'},
      });
      expect(formatDateTime(add(now, {minutes: 60}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T13:00:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 60 minutes',
          speech: 'In 60 minutes',
        },
        absolute: {text: '10 Jan 2024, 13:00'},
      });
      expect(formatDateTime(add(now, {minutes: 61}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-10T13:01:00Z')),
        relative: {
          resolution: 'minutes',
          text: 'In 61 minutes',
          speech: 'In 61 minutes',
        },
        absolute: {text: '10 Jan 2024, 13:01'},
      });
    });
    it('shows Today when day is the same', () => {
      expect(formatDateTime(sub(now, {minutes: 61}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T10:59:00Z')),
        relative: {
          resolution: 'days',
          text: 'Today, 10:59',
          speech: 'Today at 10:59',
        },
        absolute: {text: '10 Jan 2024, 10:59'},
      });
      expect(formatDateTime(add(now, {minutes: 61}), now)).toEqual({
        date: new Date(Date.parse('2024-01-10T13:01:00Z')),
        relative: {
          resolution: 'days',
          text: 'Today, 13:01',
          speech: 'Today at 13:01',
        },
        absolute: {text: '10 Jan 2024, 13:01'},
      });
    });
    it('shows Tomorrow when day is the next day', () => {
      expect(formatDateTime(add(now, {days: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-11T12:00:00Z')),
        relative: {
          resolution: 'days',
          text: 'Tomorrow, 12:00',
          speech: 'Tomorrow at 12:00',
        },
        absolute: {text: '11 Jan 2024, 12:00'},
      });
    });
    it('shows Yesterday when day is the previous day', () => {
      expect(formatDateTime(sub(now, {days: 1}), now)).toEqual({
        date: new Date(Date.parse('2024-01-09T12:00:00Z')),
        relative: {
          resolution: 'days',
          text: 'Yesterday, 12:00',
          speech: 'Yesterday at 12:00',
        },
        absolute: {text: '9 Jan 2024, 12:00'},
      });
    });
    it('shows day of week when day is beyond the previous or next day', () => {
      expect(formatDateTime(sub(now, {days: 4}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-06T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Saturday, 12:00',
          speech: 'Saturday at 12:00',
        },
        absolute: {text: '6 Jan 2024, 12:00'},
      });
      expect(formatDateTime(sub(now, {days: 3}), now)).toEqual({
        date: new Date(Date.parse('2024-01-07T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Sunday, 12:00',
          speech: 'Sunday at 12:00',
        },
        absolute: {text: '7 Jan 2024, 12:00'},
      });
      expect(formatDateTime(sub(now, {days: 2}), now)).toEqual({
        date: new Date(Date.parse('2024-01-08T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Monday, 12:00',
          speech: 'Monday at 12:00',
        },
        absolute: {text: '8 Jan 2024, 12:00'},
      });
      expect(formatDateTime(add(now, {days: 2}), now)).toEqual({
        date: new Date(Date.parse('2024-01-12T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Friday, 12:00',
          speech: 'Friday at 12:00',
        },
        absolute: {text: '12 Jan 2024, 12:00'},
      });
      expect(formatDateTime(add(now, {days: 3}), now)).toEqual({
        date: new Date(Date.parse('2024-01-13T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Saturday, 12:00',
          speech: 'Saturday at 12:00',
        },
        absolute: {text: '13 Jan 2024, 12:00'},
      });
      expect(formatDateTime(add(now, {days: 4}), now)).not.toEqual({
        date: new Date(Date.parse('2024-01-14T12:00:00Z')),
        relative: {
          resolution: 'week',
          text: 'Sunday, 12:00',
          speech: 'Sunday at 12:00',
        },
        absolute: {text: '14 Jan 2024, 12:00'},
      });
    });
    it('shows date when day is beyond the current week', () => {
      expect(formatDateTime(sub(now, {days: 4}), now)).toEqual({
        date: new Date(Date.parse('2024-01-06T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '6 Jan, 12:00',
          speech: '6th of January at 12:00',
        },
        absolute: {text: '6 Jan 2024, 12:00'},
      });
      expect(formatDateTime(add(now, {days: 4}), now)).toEqual({
        date: new Date(Date.parse('2024-01-14T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '14 Jan, 12:00',
          speech: '14th of January at 12:00',
        },
        absolute: {text: '14 Jan 2024, 12:00'},
      });
    });
    it('shows date and year when day is beyond the current week', () => {
      expect(formatDateTime(sub(now, {days: 10}), now)).toEqual({
        date: new Date(Date.parse('2023-12-31T12:00:00Z')),
        relative: {
          resolution: 'years',
          text: '31 Dec 2023, 12:00',
          speech: '31st of December, 2023 at 12:00',
        },
        absolute: {text: '31 Dec 2023, 12:00'},
      });
      expect(formatDateTime(sub(now, {days: 9}), now)).toEqual({
        date: new Date(Date.parse('2024-01-01T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '1 Jan, 12:00',
          speech: '1st of January at 12:00',
        },
        absolute: {text: '1 Jan 2024, 12:00'},
      });
      expect(formatDateTime(add(now, {days: 356}), now)).toEqual({
        date: new Date(Date.parse('2024-12-31T12:00:00Z')),
        relative: {
          resolution: 'months',
          text: '31 Dec, 12:00',
          speech: '31st of December at 12:00',
        },
        absolute: {text: '31 Dec 2024, 12:00'},
      });
      expect(formatDateTime(add(now, {days: 357}), now)).toEqual({
        date: new Date(Date.parse('2025-01-01T12:00:00Z')),
        relative: {
          resolution: 'years',
          text: '1 Jan 2025, 12:00',
          speech: '1st of January, 2025 at 12:00',
        },
        absolute: {text: '1 Jan 2025, 12:00'},
      });
    });
  });
});
