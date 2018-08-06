import React from 'react';
import {mount} from 'enzyme';

import LoadingIndicator from './LoadingIndicator';

it('should render without crashing', () => {
  mount(<LoadingIndicator />);
});

it('should create a label with the provided id', () => {
  const node = mount(<LoadingIndicator id="someId" />);

  expect(node.find('.LoadingIndicator__div')).toHaveProp('id', 'someId');
});

it('should be possible to get a smaller version', () => {
  const node = mount(<LoadingIndicator small={true} />);

  expect(node.find('.LoadingIndicator__div')).toHaveClassName('small');
});
