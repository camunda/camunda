import {expect} from 'chai';
import {includes} from 'view-utils/includes';

describe('includes', () => {
  const value = 'v1';

  it('should return true when value is in array', () => {
    const values = ['other', value];

    expect(includes(values, value)).to.eql(true);
  });

  it('should return false when value is not in array', () => {
    const values = ['other'];

    expect(includes(values, value)).to.eql(false);
  });
});
