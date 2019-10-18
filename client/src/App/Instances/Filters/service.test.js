/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  checkIsDateComplete,
  sanitizeFilter,
  checkIsVariableComplete,
  checkIsIdComplete
} from './service';
import {DEFAULT_FILTER_CONTROLLED_VALUES} from 'modules/constants';

describe('Filters/service', () => {
  describe('checkIsDateComplete', () => {
    it('should return true for YYYY-MM-DD', () => {
      expect(checkIsDateComplete('2019-01-01')).toBe(true);
    });

    it('should return true for YYYY-MM-DD HH:mm', () => {
      expect(checkIsDateComplete('2019-03-01 12:59')).toBe(true);
    });

    it('should return true for YYYY-MM-DD HH:mm:ss', () => {
      expect(checkIsDateComplete('2019-03-12 12:59:30')).toBe(true);
    });

    it('should return true for empty string', () => {
      expect(checkIsDateComplete('')).toBe(true);
    });

    it('should return false for YYY', () => {
      expect(checkIsDateComplete('201')).toBe(false);
    });

    it('should return false for YYYY-M', () => {
      expect(checkIsDateComplete('2019-0')).toBe(false);
    });

    it('should return false for YYYY-MM-D', () => {
      expect(checkIsDateComplete('2019-03-1')).toBe(false);
    });

    it('should return false for YYYY-MM-DD HH', () => {
      expect(checkIsDateComplete('2019-03-12 12')).toBe(false);
    });

    it('should return false for invalid characters', () => {
      expect(checkIsDateComplete('ABCD-EF-GH')).toBe(false);
    });

    it('should return true for date with whitespaces', () => {
      expect(checkIsDateComplete('     2019-03-12 12:59:30     ')).toBe(true);
    });

    it('should return true for only whitespace', () => {
      expect(checkIsDateComplete('     ')).toBe(true);
    });

    it('should return false for invalid date in correct format', () => {
      expect(checkIsDateComplete('2019-99-99')).toBe(false);
    });
  });

  describe('checkIsVariableComplete', () => {
    it('should be complete when both input fields have content ', () => {
      const variable = {name: 'fancyName', value: 'coolValue'};
      expect(checkIsVariableComplete(variable)).toBe(true);
    });
    it('should be complete when both input fields are empty', () => {
      const variable = {name: '', value: ''};
      expect(checkIsVariableComplete(variable)).toBe(true);
    });
    it('should not be complete, if only name is empty', () => {
      const variable = {name: '', value: 'coolValue'};
      expect(checkIsVariableComplete(variable)).toBe(false);
    });
    it('should not be complete, if only value is empty', () => {
      const variable = {name: 'fancyName', value: ''};
      expect(checkIsVariableComplete(variable)).toBe(false);
    });
  });

  describe('checkIsIdComplete', () => {
    it('should be complete on only white spaces', () => {
      expect(checkIsIdComplete('')).toBe(true);
      expect(checkIsIdComplete('      ')).toBe(true);
      expect(checkIsIdComplete('\r\n')).toBe(true);
    });

    it('should be complete on 16 digit ids', () => {
      expect(checkIsIdComplete('1234039287523094')).toBe(true);
      expect(checkIsIdComplete('1234039287523094, 1234039287523095')).toBe(
        true
      );
    });

    it('should be complete on 16-19 digit ids', () => {
      expect(checkIsIdComplete('12340392875230941')).toBe(true);
      expect(checkIsIdComplete('12340392875230941, 12340392875230942')).toBe(
        true
      );
      expect(checkIsIdComplete('12340392875230941 12340392875230942')).toBe(
        true
      );
    });

    it('should be complete when containing white spaces', () => {
      expect(
        checkIsIdComplete('1234039287523094,        1234039287523095')
      ).toBe(true);
      expect(checkIsIdComplete('    1234039287523094     ')).toBe(true);
      expect(
        checkIsIdComplete(`1234039287523094


        1234039287523094`)
      ).toBe(true);
    });

    it('should be incomplete on <16 digits', () => {
      expect(checkIsIdComplete('123')).toBe(false);
      expect(checkIsIdComplete('       123      ')).toBe(false);
      expect(checkIsIdComplete('1234039287523094, 123')).toBe(false);
    });

    it('should be incomplete when containing non digit characters', () => {
      expect(checkIsIdComplete('ABC')).toBe(false);
      expect(checkIsIdComplete('1234039287523094, ABCABCABCABCABC')).toBe(
        false
      );
      expect(checkIsIdComplete('123123123123123\n')).toBe(false);
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
          value: ''
        }
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
        startDate: '2019-10'
      });

      expect(sanitizedFilter).toEqual({});
    });

    it('should return object when date is complete', () => {
      const startDate = '2019-10-10';
      const endDate = '2019-12-12';
      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        startDate,
        endDate
      });

      expect(sanitizedFilter).toEqual({startDate, endDate});
    });

    it('should pass through values which don´t need to be santitized', () => {
      const filter = {
        active: true,
        incidents: true,
        completed: true,
        canceled: true,
        ids: '123456789',
        errorMessage: 'Bad error.',
        activityId: 'Task_1',
        version: '2',
        workflow: 'eventBasedGatewayProcess'
      };

      const sanitizedFilter = sanitizeFilter({
        ...DEFAULT_FILTER_CONTROLLED_VALUES,
        ...filter
      });

      expect(sanitizedFilter).toEqual(filter);
    });
  });
});
