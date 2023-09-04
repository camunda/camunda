/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {UIEvent} from 'react';
import {shallow} from 'enzyme';
import {MenuDropdown} from '@camunda/camunda-optimize-composite-components';

import {ignoreFragments} from 'services';

import CarbonSelect, {CarbonSelectProps} from './CarbonSelect';

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
    <CarbonSelect {...props}>
      <CarbonSelect.Option />
    </CarbonSelect>
  );
});

it('should render a .CarbonSelect className by default', () => {
  const node = shallow(
    <CarbonSelect {...props}>
      <CarbonSelect.Option />
    </CarbonSelect>
  );

  expect(node).toMatchSelector('.CarbonSelect');
});

it('should merge and render additional classNames as provided as a property', () => {
  const node = shallow(
    <CarbonSelect {...props} className="foo">
      <CarbonSelect.Option />
    </CarbonSelect>
  );

  expect(node).toMatchSelector('.CarbonSelect.foo');
});

it('should render child elements and their props', () => {
  const node = shallow(
    <CarbonSelect {...props}>
      <CarbonSelect.Option label="test_option" value="1" />
    </CarbonSelect>
  );

  expect(node.find('Option[label="test_option"]')).toExist();
  expect(node.find('Option[value="1"]')).toExist();
});

it('should select option onClick and add checked property', () => {
  const spy = jest.fn();
  const node = shallow<CarbonSelectProps>(
    <CarbonSelect {...props} onChange={spy}>
      <CarbonSelect.Option value="1" label="Option One" />
    </CarbonSelect>
  );

  node.find(CarbonSelect.Option).prop('onChange')?.({
    target: {closest: () => ({getAttribute: () => '1'})},
  } as unknown as UIEvent<HTMLElement>);
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find(CarbonSelect.Option).prop('selected')).toBeTruthy();
  expect(node.find('ForwardRef(MenuDropdown)').prop('label')).toBe('Option One');
});

it('should select submenu option onClick and set checked property on the submenu and the option', () => {
  const spy = jest.fn();
  const node = shallow(
    <CarbonSelect {...props} onChange={spy}>
      <CarbonSelect.Submenu label="submenu">
        <CarbonSelect.Option value="1" label="Option One" />
      </CarbonSelect.Submenu>
    </CarbonSelect>
  );

  node.find(CarbonSelect.Option).prop('onChange')?.({
    target: {closest: () => ({getAttribute: () => '1'})},
  } as unknown as UIEvent<HTMLElement>);
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find(CarbonSelect.Submenu).prop('selected')).toBeTruthy();
  expect(node.find(CarbonSelect.Option).prop('selected')).toBeTruthy();
  expect(node.find('ForwardRef(MenuDropdown)').prop('label')).toBe('submenu : Option One');
});

it('should allow a custom label', () => {
  const node = shallow(
    <CarbonSelect {...props} labelText="Custom Select Label">
      <CarbonSelect.Option />
    </CarbonSelect>
  );

  expect(node.find('label').text()).toBe('Custom Select Label');
});

it('should use label attribute to calculate Select button label if provided', () => {
  const node = shallow(
    <CarbonSelect {...props}>
      <CarbonSelect.Submenu label="submenu">
        <CarbonSelect.Option value="1" label="Option One">
          <b>Option</b>One
        </CarbonSelect.Option>
      </CarbonSelect.Submenu>
    </CarbonSelect>
  );

  node.setProps({value: '1'});
  expect(node.find(MenuDropdown).prop('label')).toBe('submenu : Option One');
});

it('should invoke ignoreFragments when rendering the list', async () => {
  const children = [<CarbonSelect.Submenu key="1" />, <CarbonSelect.Option key="2" />];
  shallow(<CarbonSelect {...props}>{children}</CarbonSelect>);

  expect(ignoreFragments).toHaveBeenCalledWith(children);
});

describe('CarbonSelect.Option', () => {
  it('should render the option', () => {
    const node = shallow(<CarbonSelect.Option label="label" />);

    expect(node.find('.Option').prop('label')).toBe('label');
  });
});

describe('CarbonSelect.Submenu', () => {
  it('should render the submenu', () => {
    const node = shallow(<CarbonSelect.Submenu label="label" />);

    expect(node.find('.Submenu').prop('label')).toBe('label');
  });
});
