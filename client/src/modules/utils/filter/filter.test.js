import {parseFilterForRequest, getFilterQueryString} from './filter';

import {DEFAULT_FILTER} from '../../constants/filter';

describe('parseFilterForRequest', () => {
  it('should parse empty filter selection', () => {
    const filter = {
      active: false,
      incidents: false,
      canceled: false,
      completed: false
    };

    expect(parseFilterForRequest(filter).running).toBe(false);
    expect(parseFilterForRequest(filter).incidents).toBe(false);
    expect(parseFilterForRequest(filter).active).toBe(false);

    expect(parseFilterForRequest(filter).finished).toBe(false);
    expect(parseFilterForRequest(filter).cancelled).toBe(false);
    expect(parseFilterForRequest(filter).completed).toBe(false);
  });

  describe('Running Instances Filter', () => {
    it('should parse both active and incidents filter selection', () => {
      const filter = {active: true, incidents: true};

      expect(parseFilterForRequest(filter).running).toBe(true);
      expect(parseFilterForRequest(filter).incidents).toBe(true);
      expect(parseFilterForRequest(filter).active).toBe(true);
    });
    it('should parse only active filter selection', () => {
      const filter = {active: true, incidents: false};

      expect(parseFilterForRequest(filter).running).toBe(true);
      expect(parseFilterForRequest(filter).incidents).toBe(false);
      expect(parseFilterForRequest(filter).active).toBe(true);
    });
    it('should parse only incidents filter selection', () => {
      const filter = {active: false, incidents: true};

      expect(parseFilterForRequest(filter).running).toBe(true);
      expect(parseFilterForRequest(filter).incidents).toBe(true);
      expect(parseFilterForRequest(filter).active).toBe(false);
    });
  });

  describe('Completed Instances Filter', () => {
    it('should parse both regularly completed and canceled filter selection', () => {
      const filter = {canceled: true, completed: true};
      expect(parseFilterForRequest(filter).finished).toBe(true);
      expect(parseFilterForRequest(filter).cancelled).toBe(true);
      expect(parseFilterForRequest(filter).completed).toBe(true);
    });

    it('should parse only regularly completed filter selection', () => {
      const filter = {canceled: false, completed: true};
      expect(parseFilterForRequest(filter).finished).toBe(true);
      expect(parseFilterForRequest(filter).cancelled).toBe(false);
      expect(parseFilterForRequest(filter).completed).toBe(true);
    });

    it('should parse only canceled filter selection', () => {
      const filter = {canceled: true, completed: false};
      expect(parseFilterForRequest(filter).finished).toBe(true);
      expect(parseFilterForRequest(filter).cancelled).toBe(true);
      expect(parseFilterForRequest(filter).completed).toBe(false);
    });
  });
});

describe('getFilterQueryString', () => {
  it('should return a query string', () => {
    const queryString = '?filter={"active":true,"incidents":true}';

    expect(getFilterQueryString(DEFAULT_FILTER)).toBe(queryString);
  });

  it('should support objects with various values', () => {
    const input = {a: true, b: true, c: 'X'};
    const output = '?filter={"a":true,"b":true,"c":"X"}';

    expect(getFilterQueryString(input)).toBe(output);
  });
});
