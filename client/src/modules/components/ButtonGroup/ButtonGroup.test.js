import React from 'react';
import { mount } from 'enzyme';

import ButtonGroup from './ButtonGroup';

it('should render without crashing', () => {
  mount(<ButtonGroup />);
});

it('should render its children', () => {
  const node = mount(<ButtonGroup>
    <button></button>
  </ButtonGroup>)

expect(node.find('button')).toBePresent();
});
