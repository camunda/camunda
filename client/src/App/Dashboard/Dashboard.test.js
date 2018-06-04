import React from 'react';
import {mount} from 'enzyme';
import Dashboard from './Dashboard';

jest.mock('components', () => {
  return {Input: props => <input {...props} />};
});

it('contains an input', () => {
  const node = mount(<Dashboard />);

  expect(node.find('input')).toExist();
});
