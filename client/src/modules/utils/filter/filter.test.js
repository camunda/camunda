/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  getInstanceStatePayload,
  getFilterQueryString,
  fieldParser,
  parseFilterForRequest,
  getFilterWithWorkflowIds,
  parseQueryString
} from './filter';

import {DEFAULT_FILTER} from 'modules/constants';

jest.unmock('modules/utils/date/formatDate');

const workflows = {
  demoProcess: {
    bpmnProcessId: 'demoProcess',
    name: 'New demo process',
    workflows: [
      {
        id: '6',
        name: 'New demo process',
        version: 3,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '4',
        name: 'Demo process',
        version: 2,
        bpmnProcessId: 'demoProcess'
      },
      {
        id: '1',
        name: 'Demo process',
        version: 1,
        bpmnProcessId: 'demoProcess'
      }
    ]
  },
  orderProcess: {
    bpmnProcessId: 'orderProcess',
    name: 'Order',
    workflows: []
  }
};

describe('modules/utils/filter.js', () => {
  describe('parseQueryString', () => {
    it('should return a empty object for invalid querys string', () => {
      const invalidInputA = '?filter={"active":truef,"incidents":true}';
      const invalidInputB = '?filter=';
      const invalidInputC = '';

      expect(parseQueryString(invalidInputA)).toEqual({});
      expect(parseQueryString(invalidInputB)).toEqual({});
      expect(parseQueryString(invalidInputC)).toEqual({});
    });

    it('should return an object for valid query strings', () => {
      const input =
        '?filter={"a":true,"b":true,"c":"X", "array": ["lorem", "ipsum"]}';
      const output = {
        filter: {a: true, b: true, c: 'X', array: ['lorem', 'ipsum']}
      };

      expect(parseQueryString(input)).toEqual(output);
    });

    it('should support query strings with more params', () => {
      const input = '?filter={"a":true,"b":true,"c":"X"}&extra={"extra": true}';
      const output = {
        filter: {a: true, b: true, c: 'X'},
        extra: {extra: true}
      };

      expect(parseQueryString(input)).toEqual(output);
    });
  });

  describe('getInstanceStatePayload', () => {
    it('returns false values for an empty state selection', () => {
      const filter = {
        active: false,
        incidents: false,
        canceled: false,
        completed: false
      };

      expect(getInstanceStatePayload(filter)).toEqual({});
    });

    it('return running true when active is selected', () => {
      const filter = {
        active: true,
        incidents: false,
        canceled: false,
        completed: false
      };

      expect(getInstanceStatePayload(filter)).toEqual({
        running: true
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
        running: true
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
        running: true
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
        variable: {name: 'myVar', value: '{"id": "1"}'},
        active: true,
        incidents: false,
        completed: true,
        canceled: false
      };

      // when
      const parsedFilter = parseFilterForRequest(filter);

      expect(parsedFilter.workflowIds).toEqual(['4']);
      expect(parsedFilter.ids).toEqual(['id1', 'id2', 'id3']);
      expect(parsedFilter.errorMessage).toEqual('this is an error message');
      expect(parsedFilter.startDate).toBe(undefined);
      expect(parsedFilter.endDate).toBe(undefined);
      //ci has diffrent timezone, so we ommit the timezone +0200 at the end
      expect(parsedFilter.startDateBefore).toContain('2018-10-09T00:00:00.000');
      expect(parsedFilter.startDateAfter).toContain('2018-10-08T00:00:00.000');
      expect(parsedFilter.endDateBefore).toContain('2018-10-11T00:00:00.000');
      expect(parsedFilter.endDateAfter).toContain('2018-10-10T00:00:00.000');
      expect(parsedFilter.activityId).toBe('5');
      expect(parsedFilter.variable).toEqual(filter.variable);
      expect(parsedFilter.active).toBe(true);
      expect(parsedFilter.incidents).toBe(false);
      expect(parsedFilter.completed).toBe(true);
      expect(parsedFilter.canceled).toBe(false);
      expect(parsedFilter.running).toBe(true);
      expect(parsedFilter.finished).toBe(true);
    });

    it('should trim fields with string values', () => {
      // given
      const filter = {
        workflowIds: ['4'],
        ids: `
        id1 , id2    id3
        `,
        errorMessage: '               this is an error message',
        startDate: '          2019-10-07 12:30:00',
        endDate: '  2019-10-06         ',
        activityId: '5',
        variable: {name: '   myVar  ', value: '   "1, 2\r \n "   '},
        active: true,
        incidents: false,
        completed: true,
        canceled: false
      };

      // when
      const parsedFilter = parseFilterForRequest(filter);

      expect(parsedFilter.ids).toEqual(['id1', 'id2', 'id3']);
      expect(parsedFilter.errorMessage).toEqual('this is an error message');
      expect(parsedFilter.active).toBe(true);

      // expect date values to not contain whitespace characters
      const re = /^\S*$/;
      expect(parsedFilter.startDateBefore).toMatch(re);
      expect(parsedFilter.startDateAfter).toMatch(re);
      expect(parsedFilter.endDateBefore).toMatch(re);
      expect(parsedFilter.endDateAfter).toMatch(re);

      expect(parsedFilter.variable).toEqual({
        name: 'myVar',
        value: '"1, 2\r \n "'
      });
    });
  });

  describe('getFilterQueryString', () => {
    it('should return a query string', () => {
      const encodedFilter = encodeURIComponent(
        '{"active":true,"incidents":true}'
      );
      const queryString = `?filter=${encodedFilter}`;

      expect(getFilterQueryString(DEFAULT_FILTER)).toBe(queryString);
    });

    it('should remove keys with false values', () => {
      const valueWithArray = {a: true, b: true, c: false, ids: ['a', 'b', 'c']};
      const encodedFilter = encodeURIComponent(
        '{"a":true,"b":true,"ids":["a","b","c"]}'
      );
      const output = `?filter=${encodedFilter}`;

      expect(getFilterQueryString(valueWithArray)).toBe(output);
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
    it('should separate the values by enter and tab', () => {
      const value = `1
2
3	4`;
      const output = fieldParser.ids('ids', value);

      expect(output.ids).toEqual(['1', '2', '3', '4']);
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
    it('should return date for string containing spaces', () => {
      const value = '  2019-10-17 12:30:00   ';
      const output = fieldParser.startDate('startDate', value);

      // no timezone tested, it differs on environments
      expect(output.startDateBefore).toContain('2019-10-17T12:31:00.000');
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

  describe('getFilterWithWorkflowIds', () => {
    it('should return the same filter if no workflow or version is provided', () => {
      const filter = {
        active: true,
        errorMessage: 'lorem',
        activityId: 'lorem'
      };

      expect(getFilterWithWorkflowIds(filter, workflows)).toEqual(filter);
    });

    it('should not return worflow and version fields, only workflowIds', () => {
      const filter = {
        active: true,
        errorMessage: 'lorem',
        workflow: 'demoProcess',
        version: '3'
      };

      expect(getFilterWithWorkflowIds(filter, workflows).workflow).toEqual(
        undefined
      );
      expect(getFilterWithWorkflowIds(filter, workflows).version).toEqual(
        undefined
      );
      expect(getFilterWithWorkflowIds(filter, workflows).workflowIds).toEqual([
        '6'
      ]);
    });

    it('should not return the right workflowIds when version is all', () => {
      const filter = {
        active: true,
        errorMessage: 'lorem',
        workflow: 'demoProcess',
        version: 'all'
      };

      expect(getFilterWithWorkflowIds(filter, workflows).workflowIds).toEqual([
        '6',
        '4',
        '1'
      ]);
    });
  });
});
