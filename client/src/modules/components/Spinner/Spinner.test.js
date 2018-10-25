import React from 'react';
import {shallow} from 'enzyme';

import Spinner from './Spinner';

describe('Spinner', () => {
  let node;

  it('should match snapshot', () => {
    node = shallow(<Spinner />);
    expect(node).toMatchSnapshot();
  });

  it('should accept any passed property', () => {
    node = shallow(<Spinner foo={'bar'} />);
    expect(node.props().foo).toBe('bar');
  });
});
