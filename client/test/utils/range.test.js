import {expect} from 'chai';
import {range} from 'utils';

describe('utils range', () => {
  it('should generate range of numbers', () => {
    expect(range(3, 7)).to.eql([3, 4, 5, 6, 7]);
  });
});
