import {
  getInstanceStatePayload,
  getFilterQueryString,
  fieldParser,
  parseFilterForRequest
} from './filter';

import {DEFAULT_FILTER} from 'modules/constants';

describe('modules/utils/filter.js', () => {
  describe('getInstanceStatePayload', () => {
    it('returns false values for an empty state selection', () => {
      const filter = {
        active: false,
        incidents: false,
        canceled: false,
        completed: false
      };

      expect(getInstanceStatePayload(filter)).toEqual({
        running: false,
        finished: false
      });
    });

    it('return running true when active is selected', () => {
      const filter = {
        active: true,
        incidents: false,
        canceled: false,
        completed: false
      };

      expect(getInstanceStatePayload(filter)).toEqual({
        running: true,
        finished: false
      });
    });

    it('return running true when incidents is selected', () => {
      const filter = {
        active: false,
        incidents: true,
        canceled: false,
        completed: false
      };

      expect(getInstanceStatePayload(filter)).toEqual({
        running: true,
        finished: false
      });
    });

    it('return running true when incidents is selected', () => {
      const filter = {
        active: false,
        incidents: true,
        canceled: false,
        completed: false
      };

      expect(getInstanceStatePayload(filter)).toEqual({
        running: true,
        finished: false
      });
    });

    it('return finished true when completed is selected', () => {
      const filter = {
        active: false,
        incidents: false,
        canceled: false,
        completed: true
      };

      expect(getInstanceStatePayload(filter)).toEqual({
        running: false,
        finished: true
      });
    });

    it('return finished true when canceled is selected', () => {
      const filter = {
        active: false,
        incidents: false,
        canceled: true,
        completed: false
      };

      expect(getInstanceStatePayload(filter)).toEqual({
        running: false,
        finished: true
      });
    });
  });

  describe('parseFilterForRequest', () => {
    it('should parse filters values', () => {
      // given
      const filter = {
        workflowIds: ['4'],
        ids: 'id1 , id2    id3',
        errorMessage: ' this is an error message',
        startDate: '08 October 2018',
        endDate: '10-10-2018',
        activityId: '5',
        active: true,
        incidents: false,
        completed: true,
        canceled: false
      };

      // when
      const parsedFilter = parseFilterForRequest(filter);

      expect(parsedFilter.workflowIds).toEqual(['4']);
      expect(parsedFilter.ids).toEqual(['id1', 'id2', 'id3']);
      expect(parsedFilter.errorMessage).toEqual(' this is an error message');
      expect(parsedFilter.startDate).toBe(undefined);
      expect(parsedFilter.startDateBefore).toBe('2018-10-09T00:00:00.000+0200');
      expect(parsedFilter.startDateAfter).toBe('2018-10-08T00:00:00.000+0200');
      expect(parsedFilter.endDate).toBe(undefined);
      expect(parsedFilter.endDateBefore).toBe('2018-10-11T00:00:00.000+0200');
      expect(parsedFilter.endDateAfter).toBe('2018-10-10T00:00:00.000+0200');
      expect(parsedFilter.activityId).toBe('5');
      expect(parsedFilter.active).toBe(true);
      expect(parsedFilter.incidents).toBe(false);
      expect(parsedFilter.completed).toBe(true);
      expect(parsedFilter.canceled).toBe(false);
      expect(parsedFilter.running).toBe(true);
      expect(parsedFilter.finished).toBe(true);
    });
  });

  describe('getFilterQueryString', () => {
    it('should return a query string', () => {
      const queryString = '?filter={"active":true,"incidents":true}';

      expect(getFilterQueryString(DEFAULT_FILTER)).toBe(queryString);
    });

    it('should remove keys with false values', () => {
      const valueWithArray = {a: true, b: true, c: false, ids: ['a', 'b', 'c']};
      const output = '?filter={"a":true,"b":true,"ids":["a","b","c"]}';

      expect(getFilterQueryString(valueWithArray)).toBe(output);
    });
  });

  describe('fieldParser.errorMessage()', () => {
    it('should return the value for errorMessage', () => {
      const value = 'lorem ipsum';
      const output = fieldParser.errorMessage('errorMessage', value);

      expect(output.errorMessage).toEqual(value);
    });

    it('should return null for empty value', () => {
      const value = '';
      const output = fieldParser.errorMessage('errorMessage', value);

      expect(output.errorMessage).toEqual(null);
    });
  });

  describe('fieldParser.ids()', () => {
    it('should return an array for ids', () => {
      const value = '1 2 3';
      const output = fieldParser.ids('ids', value);

      expect(output.ids).toEqual(['1', '2', '3']);
    });

    it('should separate the values by string and comma', () => {
      const value = ' 1, 2 , 3,';
      const output = fieldParser.ids('ids', value);

      expect(output.ids).toEqual(['1', '2', '3']);
    });
  });

  describe('fieldParser.startDate()', () => {
    it('should return object with two keys', () => {
      const value = 'July 05 2018';
      const output = fieldParser.startDate('startDate', value);

      expect(output.startDateAfter).toBeDefined();
      expect(output.startDateBefore).toBeDefined();
      expect(output.startDateAfter.length).toBe(28);
      expect(output.startDateBefore.length).toBe(28);
    });

    it('should return the same date for startDateAfter', () => {
      const value = 'July 05 2018';
      const output = fieldParser.startDate('startDate', value);

      // no timezone tested, it differs on environments
      expect(output.startDateAfter).toContain('2018-07-05T00:00:00.000');
    });
    it('should return startDateBefore = date + 1 day, when no time is provided', () => {
      const value = 'July 05 2018';
      const output = fieldParser.startDate('startDate', value);

      // no timezone tested, it differs on environments
      expect(output.startDateBefore).toContain('2018-07-06T00:00:00.000');
    });
    it('should return startDateBefore = date + 1 minute, when time is provided', () => {
      const value = 'July 05 2018 15:18';
      const output = fieldParser.startDate('startDate', value);

      // no timezone tested, it differs on environments
      expect(output.startDateBefore).toContain('2018-07-05T15:19:00.000');
    });
  });

  describe('fieldParser.endDate()', () => {
    it('should return object with two keys', () => {
      const value = 'July 05 2018';
      const output = fieldParser.endDate('endDate', value);

      expect(output.endDateAfter).toBeDefined();
      expect(output.endDateBefore).toBeDefined();
      expect(output.endDateAfter.length).toBe(28);
      expect(output.endDateBefore.length).toBe(28);
    });
  });
});
