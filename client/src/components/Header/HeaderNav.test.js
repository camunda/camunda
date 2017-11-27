import React from 'react';
import { mount } from 'enzyme';

import HeaderNav from './HeaderNav';

jest.mock('./HeaderNavItem', () => {return () => <li>fo</li>});

it('renders without crashing', () => {
  mount(<HeaderNav />);
});

it('renders itself and its children', () => {
  const node = mount(<HeaderNav>
    <div>foo</div>
  </HeaderNav>)

  expect(node.find('.HeaderNav')).toBePresent();
  expect(node).toIncludeText('foo');
})
