import {isUnique} from 'utils/isUnique';
import {expect} from 'chai';

describe('isUnique', () => {
  it('should return true if an element is unique in an array', () => {
    expect(isUnique(1, [0, 1, 2, 3])).to.eql(true);
  });

  it('should return false if an element occurs multiple times in the array', () => {
    expect(isUnique(1, [0, 1, 1, 1, 5])).to.eql(false);
  });

  it('should return false if an element is not included in array', () => {
    expect(isUnique(1, [0, 2, 8, 11])).to.eql(false);
  });
});
