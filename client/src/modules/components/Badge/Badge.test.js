import React from 'react';
import {shallow} from 'enzyme';

import Badge from './Badge';

describe('Badge', () => {
  it('should contain passed number', () => {
    const node = shallow(<Badge>123</Badge>);
    expect(node.contains('123')).toBe(true);
    expect(node).toMatchSnapshot();
  });
});
