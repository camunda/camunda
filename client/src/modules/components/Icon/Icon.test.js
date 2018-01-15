import React from 'react';
import { mount } from 'enzyme';

import Icon from './Icon';


it('should render without crashing', () => {
  mount(<Icon />);
});

it('should render a tag as provided as a property when using a background image', () => {
  const node = mount(<Icon renderedIn='i' />);
  
  expect(node.find('.Icon')).toHaveTagName('i');
});

it('should render an inline SVG', () => {
  const node = mount(<Icon />);
  
  expect(node.find('svg')).toBePresent();
});

it('should render a fill attribute according to fill provided as a prop', () => {
  const node = mount(<Icon fill='red'/>);
  expect(node.find('svg')).toMatchSelector('[fill="red"]');
})

it('should render an element with a class when "renderedIn" was provided as a property', () => {
  const node = mount(<Icon renderedIn='i' type='plus'/>);
  
  expect(node.find('.Icon')).toMatchSelector('.Icon--plus');
});