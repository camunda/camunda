import {
  parseFilterForRequest,
  defaultFilterSelection,
  getFilterQueryString,
  parseQueryString
} from './service';

describe('Instances service', () => {
  describe('parseFilterForRequest', () => {
    it('should parse both active and incidents filter selection', () => {
      const filter = {active: true, incidents: true};

      expect(parseFilterForRequest(filter).running).toBe(true);
      expect(parseFilterForRequest(filter).withIncidents).toBe(true);
      expect(parseFilterForRequest(filter).withoutIncidents).toBe(true);
    });
    it('should parse only active filter selection', () => {
      const filter = {active: true, incidents: false};

      expect(parseFilterForRequest(filter).running).toBe(true);
      expect(parseFilterForRequest(filter).withIncidents).toBe(false);
      expect(parseFilterForRequest(filter).withoutIncidents).toBe(true);
    });
    it('should parse only incidents filter selection', () => {
      const filter = {active: false, incidents: true};

      expect(parseFilterForRequest(filter).running).toBe(true);
      expect(parseFilterForRequest(filter).withIncidents).toBe(true);
      expect(parseFilterForRequest(filter).withoutIncidents).toBe(false);
    });
    it('should parse empty filter selection', () => {
      const filter = {active: false, incidents: false};

      expect(parseFilterForRequest(filter).running).toBe(false);
      expect(parseFilterForRequest(filter).withIncidents).toBe(false);
      expect(parseFilterForRequest(filter).withoutIncidents).toBe(false);
    });
  });

  describe('getFilterQueryString', () => {
    it('should return a query string', () => {
      const queryString = '?filter={"active":true,"incidents":true}';

      expect(getFilterQueryString(defaultFilterSelection)).toBe(queryString);
    });

    it('should support objects with various values', () => {
      const input = {a: true, b: true, c: 'X'};
      const output = '?filter={"a":true,"b":true,"c":"X"}';

      expect(getFilterQueryString(input)).toBe(output);
    });
  });

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
      const input = '?filter={"a":true,"b":true,"c":"X"}';
      const output = {filter: {a: true, b: true, c: 'X'}};

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
});
