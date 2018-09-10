import {isAnyInstanceSelected} from './service';

describe('ListFooter services', () => {
  describe('isAnyInstanceSelected', () => {
    let selection;

    it('should return false if no instances are selected', () => {
      selection = {ids: new Set(), excludeIds: new Set()};
      expect(isAnyInstanceSelected(selection)).toBe(false);
    });

    it('should return true if all instances are selected by filters', () => {
      selection = {ids: new Set(), excludeIds: new Set(), completed: true};
      expect(isAnyInstanceSelected(selection)).toBe(true);
    });

    it('should return true if single instances are selected by Id', () => {
      selection = {ids: new Set('123'), excludeIds: new Set()};
      expect(isAnyInstanceSelected(selection)).toBe(true);
    });
  });
});
