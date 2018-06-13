import React from 'react';
import {mount} from 'enzyme';
import Dashboard from './Dashboard';

jest.mock('./MetricPanel', () => {
  return () => <div>MetricPanel</div>;
});
it('contains an metric panel', () => {
  const node = mount(<Dashboard />);

  expect(node).toIncludeText('MetricPanel');
});
