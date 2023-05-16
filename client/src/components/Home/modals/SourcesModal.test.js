/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {areTenantsAvailable} from 'config';

import {getDefinitionsWithTenants, getTenantsWithDefinitions} from './service';
import {SourcesModal} from './SourcesModal';

jest.mock('./service', () => ({
  getDefinitionsWithTenants: jest.fn().mockReturnValue([
    {key: 'def1', name: 'def1Name', type: 'process', tenants: [{id: null}, {id: 'engineering'}]},
    {key: 'def2', name: 'def2Name', type: 'process', tenants: [{id: null}]},
  ]),
  getTenantsWithDefinitions: jest.fn().mockReturnValue([{id: null}, {id: 'engineering'}]),
}));

jest.mock('config', () => ({
  areTenantsAvailable: jest.fn().mockReturnValue(true),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  open: true,
  onClose: jest.fn(),
  onConfirm: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should load definitions and tenants on mount', async () => {
  shallow(<SourcesModal {...props} />);

  await runAllEffects();

  expect(getDefinitionsWithTenants).toHaveBeenCalled();
  expect(getTenantsWithDefinitions).toHaveBeenCalled();
});

it('should display key of definition if name is null', async () => {
  getDefinitionsWithTenants.mockReturnValueOnce([
    {key: 'testDef', name: null, type: 'process', tenants: [{id: 'sales'}]},
  ]);

  const node = shallow(<SourcesModal {...props} />);

  await runAllEffects();

  expect(node.find('Table').prop('body')[0][1]).toBe('testDef');
});

it('should hide tenants if they are not available', async () => {
  areTenantsAvailable.mockReturnValueOnce(false);
  const node = shallow(<SourcesModal {...props} />);

  await runAllEffects();
  await flushPromises();

  expect(getTenantsWithDefinitions).not.toHaveBeenCalled();
  expect(
    node
      .find('Table')
      .prop('head')
      .find((col) => col.id === 'tenants')
  ).not.toBeDefined();
  expect(node.find('.header Typeahead')).not.toExist();
});

it('should preselect all definitions if specified', async () => {
  const node = shallow(<SourcesModal {...props} preSelectAll />);

  await runAllEffects();

  expect(
    node
      .find('Table')
      .prop('body')
      .every((row) => row[0].props.checked)
  ).toBe(true);
});

it('should select and deselect a definition', async () => {
  const node = shallow(<SourcesModal {...props} />);

  await runAllEffects();

  node
    .find('Table')
    .prop('body')[0][0]
    .props.onSelect({target: {checked: true}});

  expect(node.find('Table').prop('body')[0][0].props.checked).toBe(true);

  node
    .find('Table')
    .prop('body')[0][0]
    .props.onSelect({target: {checked: false}});

  expect(node.find('Table').prop('body')[0][0].props.checked).toBe(false);
});

it('should selected/deselect all definitions', async () => {
  const node = shallow(<SourcesModal {...props} />);

  await runAllEffects();

  node
    .find('Table')
    .prop('head')[0]
    .label.props.onSelect({target: {checked: true}});

  expect(
    node
      .find('Table')
      .prop('body')
      .every((row) => row[0].props.checked)
  ).toBe(true);

  node
    .find('Table')
    .prop('head')[0]
    .label.props.onSelect({target: {checked: false}});

  expect(
    node
      .find('Table')
      .prop('body')
      .every((row) => !row[0].props.checked)
  ).toBe(true);
});

it('should filter definitions by tenant', async () => {
  const node = shallow(<SourcesModal {...props} />);

  await runAllEffects();

  node.find('Typeahead').simulate('change', 'engineering');

  expect(
    node
      .find('Table')
      .prop('body')
      .find((row) => row[1] === 'def2Name')
  ).toBe(undefined);
});

it('should only select the tenant used in filtering', async () => {
  const spy = jest.fn();
  const node = shallow(<SourcesModal {...props} onConfirm={spy} />);

  await runAllEffects();

  node.find('Typeahead').simulate('change', 'engineering');
  node
    .find('Table')
    .prop('head')[0]
    .label.props.onSelect({target: {checked: true}});

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith([
    {definitionKey: 'def1', definitionType: 'process', tenants: ['engineering']},
  ]);
});
