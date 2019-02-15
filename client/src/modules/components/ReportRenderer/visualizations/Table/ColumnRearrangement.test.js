import React from 'react';
import {shallow} from 'enzyme';

import ColumnRearrangement from './ColumnRearrangement';

jest.mock('services', () => {
  return {
    processRawData: jest.fn()
  };
});

it('should render child node', () => {
  const node = shallow(
    <ColumnRearrangement report={{result: {}}}>some child content</ColumnRearrangement>
  );

  expect(node).toIncludeText('some child content');
});
