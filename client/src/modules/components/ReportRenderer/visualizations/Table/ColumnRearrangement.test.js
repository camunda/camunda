import React from 'react';
import {shallow} from 'enzyme';

import ColumnRearrangement from './ColumnRearrangement';
jest.mock('./processRawData', () => jest.fn());

jest.mock('services', () => {
  return {
    flatten: jest.fn()
  };
});

it('should render child node', () => {
  const node = shallow(
    <ColumnRearrangement report={{result: {}}}>some child content</ColumnRearrangement>
  );

  expect(node).toIncludeText('some child content');
});
