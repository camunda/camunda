/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import {ignoreFragments} from 'services';

import {Dropdown} from 'components';

import Select from './Select';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    getHighlightedText: () => 'got highlight',
  },
  ignoreFragments: jest.fn().mockImplementation((children) => children),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

it('should render without crashing', () => {
  shallow(<Select />);
});

it('should render a .Select className by default', () => {
  const node = shallow(<Select />);

  expect(node).toMatchSelector('.Select');
});

it('should merge and render additional classNames as provided as a property', () => {
  const node = shallow(<Select className="foo" />);

  expect(node).toMatchSelector('.Select.foo');
});

it('should render child elements and their props', () => {
  const node = shallow(
    <Select>
      <Select.Option id="test_option" value="1">
        Option One
      </Select.Option>
    </Select>
  );

  expect(node.find('#test_option')).toExist();
  expect(node.find('Option[value="1"]')).toExist();
});

it('should select option onClick and add checked property', () => {
  const spy = jest.fn();
  const node = shallow<Select>(
    <Select onChange={spy}>
      <Select.Option value="1">Option One</Select.Option>
    </Select>
  );

  node.find('Option').simulate('click', {target: {closest: () => ({getAttribute: () => '1'})}});
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find('Option').prop('checked')).toBeTruthy();
  expect(node.find('Dropdown').prop('label')).toBe('Option One');
});

it('should select submenu option onClick and set checked property on the submenu and the option', () => {
  const spy = jest.fn();
  const node = shallow(
    <Select onChange={spy}>
      <Select.Submenu label="submenu">
        <Select.Option value="1">Option One</Select.Option>
      </Select.Submenu>
    </Select>
  );

  node.find('Option').simulate('click', {target: {closest: () => ({getAttribute: () => '1'})}});
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find('Submenu').props().checked).toBeTruthy();
  expect(node.find('Option').props().checked).toBeTruthy();
  expect(node.find('Dropdown').prop('label')).toBe('submenu : Option One');
});

it('should allow a custom label', () => {
  const node = shallow(<Select label="Custom Select Label" />);

  expect(node.find(Dropdown).prop('label')).toBe('Custom Select Label');
});

it('should use label attribute to calculate Select button label if provided', () => {
  const node = shallow(
    <Select>
      <Select.Submenu label="submenu">
        <Select.Option value="1" label="Option One">
          <b>Option</b>One
        </Select.Option>
      </Select.Submenu>
    </Select>
  );

  node.setProps({value: '1'});
  expect(node.find('Dropdown').prop('label')).toBe('submenu : Option One');
});

it('should invoke ignoreFragments when rendering the list', async () => {
  const children = [<Select.Submenu key="1" />, <Select.Option key="2" />];
  shallow(<Select>{children}</Select>);

  expect(ignoreFragments).toHaveBeenCalledWith(children);
});

describe('Select.Option', () => {
  it('should render the option', () => {
    const node = shallow(<Select.Option label="label" />);

    expect(node.find('ForwardRef(DropdownOption)').prop('label')).toBe('label');
  });
});

describe('Select.Submenu', () => {
  it('should render the submenu', () => {
    const node = shallow(<Select.Submenu label="label" />);

    expect(node.find('Submenu').prop('label')).toBe('label');
  });
});
