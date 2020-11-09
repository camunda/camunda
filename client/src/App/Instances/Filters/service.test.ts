/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  isDateComplete,
  isDateValid,
  sanitizeFilter,
  isVariableNameComplete,
  isVariableValueComplete,
  isIdComplete,
  isBatchOperationIdComplete,
  isBatchOperationIdValid,
  getFlowNodeOptions,
} from './service';
import {DEFAULT_FILTER_CONTROLLED_VALUES} from 'modules/constants';

describe('Filters/service', () => {
  describe('isDateComplete', () => {
    it('should return true for YYYY-MM-DD', () => {
      expect(isDateComplete('2019-01-01')).toBe(true);
    });

    it('should return true for YYYY-MM-DD hh:mm', () => {
      expect(isDateComplete('2019-03-01 12:59')).toBe(true);
    });

    it('should return true for YYYY-MM-DD hh:mm:ss', () => {
      expect(isDateComplete('2019-03-12 12:59:30')).toBe(true);
    });

    it('should return true for empty string', () => {
      expect(isDateComplete('')).toBe(true);
    });

    it('should return false for YYY', () => {
      expect(isDateComplete('201')).toBe(false);
    });

    it('should return false for YYYY-M', () => {
      expect(isDateComplete('2019-0')).toBe(false);
    });

    it('should return false for YYYY-MM-D', () => {
      expect(isDateComplete('2019-03-1')).toBe(false);
    });

    it('should return false for YYYY-MM-DD hh', () => {
      expect(isDateComplete('2019-03-12 12')).toBe(false);
    });

    it('should return false for invalid characters', () => {
      expect(isDateComplete('ABCD-EF-GH')).toBe(false);
    });

    it('should return true for date with whitespaces', () => {
      expect(isDateComplete('     2019-03-12 12:59:30     ')).toBe(true);
    });

    it('should return true for only whitespace', () => {
      expect(isDateComplete('     ')).toBe(true);
    });

    it('should return false for invalid date in correct format', () => {
      expect(isDateComplete('2019-99-99')).toBe(false);
    });
  });

  describe('isDateValid', () => {
    it('should return true for YYYY-MM-DD hh:mm:ss', () => {
      expect(isDateValid('2019-03-12 12:59:30')).toBe(true);
    });

    it('should return true for empty string', () => {
      expect(isDateValid('')).toBe(true);
    });

    it('should return false for invalid characters', () => {
      expect(isDateValid('ABCD-EF-GH')).toBe(false);
    });

    it('should return true for date with whitespaces', () => {
      expect(isDateValid('     2019-03-12 12:59:30     ')).toBe(true);
    });

    it('should return true for only whitespace', () => {
      expect(isDateValid('     ')).toBe(true);
    });
  });

  describe('isVariableNameComplete', () => {
    it('should be complete when both input fields have content ', () => {
      const variable = {name: 'fancyName', value: 'coolValue'};
      expect(isVariableNameComplete(variable)).toBe(true);
    });
    it('should be complete when both input fields are empty', () => {
      const variable = {name: '', value: ''};
      expect(isVariableNameComplete(variable)).toBe(true);
    });
    it('should be complete, if name is set and value is empty', () => {
      const variable = {name: 'fancyName', value: ''};
      expect(isVariableNameComplete(variable)).toBe(true);
    });
    it('should not be complete, if only name is empty', () => {
      const variable = {name: '', value: 'coolValue'};
      expect(isVariableNameComplete(variable)).toBe(false);
    });
  });

  describe('isVariableValueComplete', () => {
    it('should be complete when both input fields have content ', () => {
      const variable = {name: 'fancyName', value: 'coolValue'};
      expect(isVariableValueComplete(variable)).toBe(true);
    });
    it('should be complete when both input fields are empty', () => {
      const variable = {name: '', value: ''};
      expect(isVariableValueComplete(variable)).toBe(true);
    });
    it('should be complete, if only name is empty', () => {
      const variable = {name: '', value: 'coolValue'};
      expect(isVariableValueComplete(variable)).toBe(true);
    });
    it('should not be complete, if only value is empty', () => {
      const variable = {name: 'fancyName', value: ''};
      expect(isVariableValueComplete(variable)).toBe(false);
    });
  });

  describe('isIdComplete', () => {
    it('should be complete on only white spaces', () => {
      expect(isIdComplete('')).toBe(true);
      expect(isIdComplete('      ')).toBe(true);
      expect(isIdComplete('\r\n')).toBe(true);
    });

    it('should be complete on 16 digit ids', () => {
      expect(isIdComplete('1234039287523094')).toBe(true);
      expect(isIdComplete('1234039287523094, 1234039287523095')).toBe(true);
    });

    it('should be complete on 16-19 digit ids', () => {
      expect(isIdComplete('12340392875230941')).toBe(true);
      expect(isIdComplete('12340392875230941, 12340392875230942')).toBe(true);
      expect(isIdComplete('12340392875230941 12340392875230942')).toBe(true);
    });

    it('should be complete when containing white spaces', () => {
      expect(isIdComplete('1234039287523094,        1234039287523095')).toBe(
        true
      );
      expect(isIdComplete('    1234039287523094     ')).toBe(true);
      expect(
        isIdComplete(`1234039287523094


        1234039287523094`)
      ).toBe(true);
    });

    it('should be incomplete on <16 digits', () => {
      expect(isIdComplete('123')).toBe(false);
      expect(isIdComplete('       123      ')).toBe(false);
      expect(isIdComplete('1234039287523094, 123')).toBe(false);
    });

    it('should be incomplete when containing non digit characters', () => {
      expect(isIdComplete('ABC')).toBe(false);
      expect(isIdComplete('1234039287523094, ABCABCABCABCABC')).toBe(false);
      expect(isIdComplete('123123123123123\n')).toBe(false);
    });
  });

  describe('sanitizeFilter', () => {
    it('should return empty object when all values are default', () => {
      const sanitizedFilter = sanitizeFilter(DEFAULT_FILTER_CONTROLLED_VALUES);

      expect(sanitizedFilter).toEqual({});
    });

    it('should return empty object when variable is empty', () => {
      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        variable: {
          name: '',
          value: '',
        },
      });

      expect(sanitizedFilter).toEqual({});
    });

    it('should return empty object when variable is not set', () => {
      const {
        variable,
        ...filterWithoutVariable
      } = DEFAULT_FILTER_CONTROLLED_VALUES;

      const sanitizedFilter = sanitizeFilter(filterWithoutVariable);

      expect(sanitizedFilter).toEqual({});
    });

    it('should return empty object when date is incomplete', () => {
      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        startDate: '2019-10',
      });

      expect(sanitizedFilter).toEqual({});
    });

    it('should return object when date is complete', () => {
      const startDate = '2019-10-10';
      const endDate = '2019-12-12';
      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        startDate,
        endDate,
      });

      expect(sanitizedFilter).toEqual({startDate, endDate});
    });

    it('should return empty object when batch operation id is incomplete', () => {
      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        batchOperationId: 'test',
      });

      expect(sanitizedFilter).toEqual({});
    });

    it('should return object when batch operation id is complete', () => {
      const batchOperationId = '8d5aeb73-193b-4bec-a237-8ff71ac1d713';
      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        batchOperationId,
      });

      expect(sanitizedFilter).toEqual({batchOperationId});
    });

    it('should pass through values which don´t need to be santitized', () => {
      const filter = {
        active: true,
        incidents: true,
        completed: true,
        canceled: true,
        ids: '1234567891234567',
        errorMessage: 'Bad error.',
        activityId: 'Task_1',
        version: '2',
        workflow: 'eventBasedGatewayProcess',
      };

      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        ...filter,
      });

      expect(sanitizedFilter).toEqual(filter);
    });
  });

  describe('isBatchOperationIdComplete', () => {
    it('should return true for valid format', () => {
      expect(
        isBatchOperationIdComplete('8d5aeb73-193b-4bec-a237-8ff71ac1d713')
      ).toBe(true);
    });

    it('should return false for invalid format', () => {
      expect(
        isBatchOperationIdComplete('193b-4bec-8d5aeb73-a237-8ff71ac1d713')
      ).toBe(false);
      expect(
        isBatchOperationIdComplete('8d5aeb732193b24bec2a23728ff71ac1d713')
      ).toBe(false);
      expect(
        isBatchOperationIdComplete('------------------------------------')
      ).toBe(false);
      expect(
        isBatchOperationIdComplete('111111111111111111111111111111111111')
      ).toBe(false);
      expect(
        isBatchOperationIdComplete('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')
      ).toBe(false);
    });

    it('should return false for invalid characters', () => {
      [
        '!',
        '@',
        '#',
        '$',
        '%',
        '^',
        '&',
        '*',
        '(',
        ')',
        '_',
        '=',
        '+',
        '[',
        ']',
        '{',
        '}',
        "'",
        '"',
        ';',
        ':',
        ',',
        '.',
        '<',
        '>',
        '?',
        '/',
        '\\',
        '|',
        '`',
        '~',
        // @ts-expect-error ts-migrate(2569) FIXME: Type 'string' is not an array type or a string typ... Remove this comment to see the full error message
      ].forEach(([input]) => {
        expect(isBatchOperationIdValid(`${input}`)).toEqual(false);
      });
    });
  });

  describe('isBatchOperationIdValid', () => {
    it('should return true for valid characters', () => {
      expect(isBatchOperationIdValid('1')).toBe(true);
      expect(isBatchOperationIdValid('a')).toBe(true);
      expect(isBatchOperationIdValid('-')).toBe(true);
    });

    it('should check max length correctly', () => {
      expect(
        isBatchOperationIdValid('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')
      ).toBe(true);
      expect(
        isBatchOperationIdValid('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')
      ).toBe(false);
    });

    it('should return false for invalid characters', () => {
      [
        '!',
        '@',
        '#',
        '$',
        '%',
        '^',
        '&',
        '*',
        '(',
        ')',
        '_',
        '=',
        '+',
        '[',
        ']',
        '{',
        '}',
        "'",
        '"',
        ';',
        ':',
        ',',
        '.',
        '<',
        '>',
        '?',
        '/',
        '\\',
        '|',
        '`',
        '~',
        // @ts-expect-error ts-migrate(2569) FIXME: Type 'string' is not an array type or a string typ... Remove this comment to see the full error message
      ].forEach(([input]) => {
        expect(isBatchOperationIdValid(input)).toBe(false);
      });
    });
  });

  describe('getFlowNodeOptions', () => {
    it('should return empty', () => {
      expect(getFlowNodeOptions([])).toEqual([]);
    });

    it('should map correctly', () => {
      const unsortedSelectableFlowNodes = [
        {id: 'Event_C', $type: 'bpmn:StartEvent', name: 'Z Event'},
        {id: 'Event_A', $type: 'bpmn:StartEvent', name: 'A Event'},
        {id: 'Activity_D', $type: 'bpmn:EndEvent'},
        {id: 'Activity_B', $type: 'bpmn:EndEvent'},
      ];
      const result = getFlowNodeOptions(unsortedSelectableFlowNodes);
      expect(result[0].label).toEqual('A Event');
      expect(result[1].label).toEqual('Activity_B');
      expect(result[2].label).toEqual('Activity_D');
      expect(result[3].label).toEqual('Z Event');
    });
  });
});
