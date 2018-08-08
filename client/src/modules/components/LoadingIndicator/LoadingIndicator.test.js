import React from 'react';
import {mount} from 'enzyme';

import LoadingIndicator from './LoadingIndicator';

it('should render without crashing', () => {
  mount(<LoadingIndicator />);
});

it('should create a label with the provided id', () => {
  const node = mount(<LoadingIndicator id="someId" />);

  expect(node.find('.sk-circle')).toHaveProp('id', 'someId');
});

it('should be possible to get a smaller version', () => {
  const node = mount(<LoadingIndicator small />);

  expect(node.find('.sk-circle')).toHaveClassName('small');
});
