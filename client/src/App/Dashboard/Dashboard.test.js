import React from 'react';
import {mount} from 'enzyme';
import Dashboard from './Dashboard';

jest.mock('./StatsPanel', () => {
  return {StatsPanel: () => <div>StatsPanel</div>};
});
it('contains an statistics panel', () => {
  const node = mount(<Dashboard />);

  expect(node).toIncludeText('MetricPanel');
});
