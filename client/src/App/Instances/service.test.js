import {parseFilterForRequest} from './service';

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
});
