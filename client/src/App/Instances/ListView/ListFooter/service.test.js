import {isAnyInstanceSelected} from './service';

describe('ListFooter services', () => {
  describe('isAnyInstanceSelected', () => {
    let selection;

    it('should return false if no instances are selected', () => {
      selection = {ids: [], excludeIds: []};
      expect(isAnyInstanceSelected(selection)).toBe(false);
    });

    it('should return true if all instances are selected by filters', () => {
      selection = {ids: [], excludeIds: [], completed: true};
      expect(isAnyInstanceSelected(selection)).toBe(true);
    });

    it('should return true if single instances are selected by Id', () => {
      selection = {ids: ['123'], excludeIds: []};
      expect(isAnyInstanceSelected(selection)).toBe(true);
    });
  });
});
