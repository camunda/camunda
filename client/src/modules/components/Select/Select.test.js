import React from 'react';
import {mount} from 'enzyme';

import Select from './Select';

it('should render without crashing', () => {
  mount(<Select />);
});

it('should render a .Select className by default', () => {
  const node = mount(<Select />);

  expect(node.find('select')).toMatchSelector('.Select');
});

it('should merge and render additional classNames as provided as a property', () => {
  const node = mount(<Select className="foo" />);

  expect(node.find('select')).toMatchSelector('.Select.foo');
});

it('should render child elements and their props', () => {
  const node = mount(
    <Select>
      <Select.Option id="test_option" value="1">
        Option One
      </Select.Option>
    </Select>
  );

  expect(node.find('#test_option')).toBePresent();
  expect(node.find('option[value="1"]')).toBePresent();
});

it('should translate the isInvalid props to is-invalid className', () => {
  const node = mount(<Select className="foo" />);
  node.setProps({isInvalid: true});

  expect(node.getDOMNode().className.includes('is-invalid')).toBe(true);
});
