import React from 'react';
import {mount} from 'enzyme';

import ActionItem from './ActionItem';

jest.mock('components', () => {
  return {
    Button: props => <button {...props}>{props.children}</button>
  }
});

it('should have an action button', () => {
  const node = mount(<ActionItem/>);

  expect(node.find('button')).toBePresent();
});

it('should render child content', () => {
  const node = mount(<ActionItem>Some child content</ActionItem>);

  expect(node.find('span')).toIncludeText('Some child content');
});

it('should call the onClick handler', () => {
  const spy = jest.fn();
  const node = mount(<ActionItem onClick={spy}>Content</ActionItem>);

  node.find('button').simulate('click');

  expect(spy).toHaveBeenCalled();
});
