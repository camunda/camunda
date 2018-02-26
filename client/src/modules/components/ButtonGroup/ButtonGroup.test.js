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

it('should apply passed classNames', () => {
  const node = mount(<ButtonGroup className='CustomClass' />);

  expect(node.find('.CustomClass')).toBePresent();
});

it('should also work when not providing classNames', () => {
  const node = mount(<ButtonGroup />);

  expect(node.find('.ButtonGroup')).toBePresent();
});
