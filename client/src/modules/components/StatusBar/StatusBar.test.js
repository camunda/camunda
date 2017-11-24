import React from 'react';
import { mount } from 'enzyme';

import StatusBar from './StatusBar';


it('should render without crashing', () => {
  mount(<StatusBar />);
});

it('should render addiontal classNames as provided as a property', () => {
  const node = mount(<StatusBar className='Foo' />);

  expect(node.find('.StatusBar')).toMatchSelector('.Foo');
});

it('should render a height as provided as a property', () => {
  const node = mount(<StatusBar height='12px' />);

  expect(node.find('.StatusBar').getDOMNode().style.height).toBe('12px');
});

it('should render an indicator representing a value provided as a property', () => {
  const node = mount(<StatusBar status={50} />);

  expect(node.find('.StatusBar__indicator').getDOMNode().style.width).toBe('50%');
});
