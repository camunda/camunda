import React from 'react';
import { mount } from 'enzyme';

import Button from './Button';

it('should render without crashing', () => {
  mount(<Button />);
});

it('renders a <button> element by default', () => {
  const node = mount(<Button />);

  expect(node).toHaveTagName('Button');
});

it('renders a <a> element when specified as a property', () => {
  const type = 'a'

  const node = mount(<Button tag={type} />);
  expect(node.find('.Button')).toHaveTagName('a');
});

it('renders a label as provided as a property', () => {
  const text = 'Click Me';

  const node = mount(<Button label={text} />);
  expect(node).toIncludeText(text);
});

it('renders a modifier class name based on the type provided as a property', () => {
  const type = 'danger'

  const node = mount(<Button type={type} />);
  expect(node.find('button')).toHaveClassName('Button--danger');
});

it('renders the id as provided as a property', () => {
  const id = 'my-button';

  const node = mount(<Button id={id} />);
  expect(node.find('button')).toMatchSelector('#' + id);
});

it('does render the title as provided as a property', () => {
  const titleText = 'my-button';

  const node = mount(<Button title={titleText} />);
  expect(node.find('button')).toMatchSelector('button[title="' + titleText + '"]');
});

it('executes a click handler as provided as a property', () => {
  const handler = jest.fn();
  const node = mount(<Button onClick={handler} />);

  node.find('button').simulate('click');
  expect(handler).toHaveBeenCalled();
});
