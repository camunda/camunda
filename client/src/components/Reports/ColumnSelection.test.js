import React from 'react';
import {mount} from 'enzyme';

import ColumnSelection from './ColumnSelection';

jest.mock('components', () => {
  return {
    Popover: ({children}) => <div>{children}</div>,
    Switch: props => <input type="checkbox" {...props} />
  };
});

it('should have a switch for every column', () => {
  const node = mount(<ColumnSelection columns={{a: 1, b: 2, c: 3, variables: {x: 1, y: 2}}} />);

  expect(node.find('input[type="checkbox"]').length).toBe(5);
});

it('should call the onChange handler', () => {
  const spy = jest.fn();
  const node = mount(<ColumnSelection columns={{a: 1, variables: {}}} onChange={spy} />);

  node.find('input[type="checkbox"]').simulate('change', {target: {checked: false}});

  expect(spy).toHaveBeenCalledWith(['a']);
});
