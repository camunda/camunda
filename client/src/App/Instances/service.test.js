import {parseFilterForRequest} from './service';
const activeAndIncidents = {
  running: true
};
//do we want to send just {running: false}
const empty = {running: false, withIncidents: false, withoutIncidents: false};
const active = {running: true, withIncidents: false, withoutIncidents: true};
const incidents = {running: true, withIncidents: true, withoutIncidents: false};

describe('Instances service', () => {
  describe('parseFilterForRequest', () => {
    it('should parse both active and incidents filter selection', () => {
      const filter = {active: true, incidents: true};
      expect(parseFilterForRequest(filter)).toEqual(activeAndIncidents);
    });
    it('should parse only active filter selection', () => {
      const filter = {active: true, incidents: false};
      expect(parseFilterForRequest(filter)).toEqual(active);
    });
    it('should parse only incidents filter selection', () => {
      const filter = {active: false, incidents: true};
      expect(parseFilterForRequest(filter)).toEqual(incidents);
    });
    it('should parse empty filter selection', () => {
      const filter = {active: false, incidents: false};
      expect(parseFilterForRequest(filter)).toEqual(empty);
    });
  });
});
