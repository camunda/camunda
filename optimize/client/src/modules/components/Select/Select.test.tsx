/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {UIEvent} from 'react';
import {shallow} from 'enzyme';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';
import {MenuItem} from '@carbon/react';

import {ignoreFragments} from 'services';

import Select, {SelectProps} from './Select';

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

const props = {id: 'id'};

it('should render without crashing', () => {
  shallow(
    <Select {...props}>
      <Select.Option />
    </Select>
  );
});

it('should render a .Select className by default', () => {
  const node = shallow(
    <Select {...props}>
      <Select.Option />
    </Select>
  );

  expect(node).toMatchSelector('.Select');
});

it('should merge and render additional classNames as provided as a property', () => {
  const node = shallow(
    <Select {...props} className="foo">
      <Select.Option />
    </Select>
  );

  expect(node).toMatchSelector('.Select.foo');
});

it('should render child elements and their props', () => {
  const node = shallow(
    <Select {...props}>
      <Select.Option label="test_option" value="1" />
    </Select>
  );

  expect(node.find('Option[label="test_option"]')).toExist();
  expect(node.find('Option[value="1"]')).toExist();
});

it('should select option onClick and add checked property', () => {
  const spy = jest.fn();
  const node = shallow<SelectProps>(
    <Select {...props} onChange={spy}>
      <Select.Option value="1" label="Option One" />
    </Select>
  );

  node.find(Select.Option).simulate('change', {
    target: {closest: () => ({getAttribute: () => '1'})},
  } as unknown as UIEvent<HTMLElement>);
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find(Select.Option).prop('selected')).toBeTruthy();
  expect(node.find('ForwardRef(MenuDropdown)').prop('label')).toBe('Option One');
});

it('should select submenu option onClick and set checked property on the submenu and the option', () => {
  const spy = jest.fn();
  const node = shallow(
    <Select {...props} onChange={spy}>
      <Select.Submenu label="submenu">
        <Select.Option value="1" label="Option One" />
      </Select.Submenu>
    </Select>
  );

  node.find(Select.Option).simulate('change', {
    target: {closest: () => ({getAttribute: () => '1'})},
  } as unknown as UIEvent<HTMLElement>);
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find(Select.Submenu).prop('selected')).toBeTruthy();
  expect(node.find(Select.Option).prop('selected')).toBeTruthy();
  expect(node.find('ForwardRef(MenuDropdown)').prop('label')).toBe('submenu : Option One');
});

it('should allow a custom label', () => {
  const node = shallow(
    <Select {...props} labelText="Custom Select Label">
      <Select.Option />
    </Select>
  );

  expect(node.find('label').text()).toBe('Custom Select Label');
});

it('should allow a custom helper', () => {
  const node = shallow(
    <Select {...props} helperText="Custom Helper Text">
      <Select.Option />
    </Select>
  );

  expect(node.find('div').at(1).text()).toBe('Custom Helper Text');
});

it('should use label attribute to calculate Select button label if provided', () => {
  const node = shallow(
    <Select {...props}>
      <Select.Submenu label="submenu">
        <Select.Option value="1" label="Option One">
          <b>Option</b>One
        </Select.Option>
      </Select.Submenu>
    </Select>
  );

  node.setProps({value: '1'});
  expect(node.find(MenuDropdown).prop('label')).toBe('submenu : Option One');
});

it('should invoke ignoreFragments when rendering the list', async () => {
  const children = [<Select.Submenu key="1" />, <Select.Option key="2" />];
  shallow(<Select {...props}>{children}</Select>);

  expect(ignoreFragments).toHaveBeenCalledWith(children);
});

describe('Select.Option', () => {
  it('should render the option', () => {
    const node = shallow(<Select.Option label="label" />);

    expect(node.find('.Option').prop('label')).toBe('label');
  });

  it('should handle disabled state', () => {
    const node = shallow(
      <Select.Option label="label" disabled>
        <b>Option</b>
      </Select.Option>
    );

    expect(node.dive().find(MenuItem).prop('disabled')).toBeTruthy();
    expect(node.find('b')).not.toExist();
  });
});

describe('Select.Submenu', () => {
  it('should render the submenu', () => {
    const node = shallow(<Select.Submenu label="label" />);

    expect(node.find('.Submenu').prop('label')).toBe('label');
  });

  it('should handle disabled state', () => {
    const node = shallow(
      <Select.Submenu label="label" disabled>
        <b>Option</b>
      </Select.Submenu>
    );

    expect(node.dive().find(MenuItem).prop('disabled')).toBeTruthy();
    expect(node.find('b')).not.toExist();
  });
});
