import React from 'react';
import { mount } from 'enzyme';

import ProgressBar from './ProgressBar';


it('should render without crashing', () => {
  mount(<ProgressBar />);
});

it('should render addiontal classNames as provided as a property', () => {
  const node = mount(<ProgressBar className='Foo' />);

  expect(node.find('.ProgressBar')).toMatchSelector('.Foo');
});

it('should render a height as provided as a property', () => {
  const node = mount(<ProgressBar height='12px' />);

  expect(node.find('.ProgressBar').getDOMNode().style.height).toBe('12px');
});

it('should render an indicator representing a value provided as a property', () => {
  const node = mount(<ProgressBar status={50} />);

  expect(node.find('.ProgressBar__indicator').getDOMNode().style.width).toBe('50%');
});
