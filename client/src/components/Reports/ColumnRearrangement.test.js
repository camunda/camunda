import React from 'react';
import {mount} from 'enzyme';

import ColumnRearrangementAddon from './ColumnRearrangement';

const ColumnRearrangement = ColumnRearrangementAddon.Wrapper;

jest.mock('services', () => {
  return {
    processRawData: jest.fn()
  };
});

jest.mock('./service', () => {
  return {
    isRawDataReport: () => true
  };
});

it('should render child node', () => {
  const node = mount(<ColumnRearrangement>some child content</ColumnRearrangement>);

  expect(node).toIncludeText('some child content');
});
