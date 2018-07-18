import React from 'react';
import {shallow} from 'enzyme';
import Textarea from './Textarea';

describe('Textarea', () => {
  it('should match snapshot', () => {
    const node = shallow(<Textarea />);
    expect(node).toMatchSnapshot();
  });
});
