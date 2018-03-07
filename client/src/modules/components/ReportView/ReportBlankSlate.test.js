import React from 'react';
import {mount} from 'enzyme';

import ReportBlankSlate from './ReportBlankSlate';

it('should render without crashing', () => {
  mount(<ReportBlankSlate />);
});

it('should render a message provided as a prop', () => {
  const node = mount(<ReportBlankSlate message="foo" />);

  expect(node.find('.ReportBlankSlate__message')).toIncludeText('foo');
});
