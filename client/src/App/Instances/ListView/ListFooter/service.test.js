import {isAnyInstanceSelected} from './service';

describe('ListFooter services', () => {
  describe('isAnyInstanceSelected', () => {
    let selection;

    it('should return false if no instances are selected', () => {
      selection = {all: false, ids: [], excludeIds: []};
      expect(isAnyInstanceSelected(selection)).toBe(false);
    });

    it('should return true if all instances are selected by filters', () => {
      selection = {all: true, ids: [], excludeIds: []};
      expect(isAnyInstanceSelected(selection)).toBe(true);
    });

    it('should return true if single instances are selected by Id', () => {
      selection = {all: false, ids: ['123'], excludeIds: []};
      expect(isAnyInstanceSelected(selection)).toBe(true);
    });

    it('should return true if single instances is not selected by Id', () => {
      selection = {all: true, ids: [], excludeIds: ['124']};
      expect(isAnyInstanceSelected(selection)).toBe(true);
    });
  });
});
