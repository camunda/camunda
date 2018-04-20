import React from 'react';
import {mount} from 'enzyme';

import ColumnRearrangement from './ColumnRearrangement';

jest.mock('services', () => {
  return {
    processRawData: jest.fn()
  };
});

it('should render child node', () => {
  const node = mount(<ColumnRearrangement>some child content</ColumnRearrangement>);

  expect(node).toIncludeText('some child content');
});
