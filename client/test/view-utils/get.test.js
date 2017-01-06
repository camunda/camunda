import {expect} from 'chai';
import {get} from 'view-utils/get';

describe('get', () => {
  it('should return given property from object', () => {
    const property = 'prop-1';
    const value = 'val-1';
    const object = {
      [property]: value
    };

    expect(get(property, object)).to.eql(value);
  });
});
