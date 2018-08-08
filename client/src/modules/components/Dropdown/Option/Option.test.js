import React from 'react';
import {shallow} from 'enzyme';

import Option from './Option';

describe('Option', () => {
  let Child, node, spy;
  beforeEach(() => {
    Child = () => <span>I am a label</span>;
    spy = jest.fn();
    node = shallow(
      <Option onClick={spy}>
        <Child />
      </Option>
    );
  });

  it('should renders its children', () => {
    expect(node.find(Child)).toExist();
  });

  it('should pass properties', () => {
    node.simulate('click');
    expect(spy).toHaveBeenCalled();
  });
});
