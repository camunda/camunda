import React from 'react';
import { mount } from 'enzyme';

import ControlGroup from './ControlGroup';

it('should render without crashing', () => {
  mount(<ControlGroup />);
});

it('should render its children', () => {
  const node = mount(<ControlGroup>
    <div className='foo'></div>
  </ControlGroup>);
  
  expect(node.find('.foo')).toBePresent();
});

it('should render additonal classNames', () => {
  const node = mount(<ControlGroup className='bar' />);

  expect(node.find('.ControlGroup.bar')).toBePresent();
});