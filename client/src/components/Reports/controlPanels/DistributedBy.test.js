/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Select, Input} from 'components';
import {reportConfig, createReportUpdate} from 'services';
import {getOptimizeProfile} from 'config';

import DistributedBy from './DistributedBy';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    reportConfig: {
      ...rest.reportConfig,
      process: {
        distribution: [],
      },
    },
    createReportUpdate: jest.fn(),
  };
});

const config = {
  type: 'process',
  variables: [],
  onChange: jest.fn(),
  report: {
    groupBy: {type: 'group'},
    distributedBy: {type: 'distribution'},
  },
};

beforeEach(() => {
  reportConfig.process.distribution = [
    {
      key: 'none',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('None'),
    },
    {
      key: 'distribution1',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Distribution  1'),
    },
    {
      key: 'distribution2',
      matcher: jest.fn().mockReturnValue(true),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Distribution  2'),
    },
    {
      key: 'variable',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Variable'),
    },
    {
      key: 'assignee',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('assignee'),
    },
  ];

  createReportUpdate.mockClear();
});

it('should disable options which would create a wrong combination', () => {
  reportConfig.process.distribution[1].enabled.mockReturnValue(false);

  const node = shallow(<DistributedBy {...config} />);

  expect(node.find(Select.Option).first()).toBeDisabled();
});

it('should disable the variable view submenu if there are no variables', () => {
  const node = shallow(<DistributedBy {...config} />);

  expect(node.find(Select.Submenu)).toBeDisabled();
});

it('invoke configUpdate with the correct variable data', async () => {
  const spy = jest.fn();
  const node = shallow(
    <DistributedBy
      {...config}
      variables={[{id: 'test', type: 'date', name: 'testName'}]}
      onChange={spy}
    />
  );

  const selectedOption = {
    type: 'variable',
    value: {id: 'test', name: 'testName', type: 'date'},
  };

  createReportUpdate.mockReturnValue({content: 'change'});

  node.find(Select).simulate('change', 'variable_testName');

  expect(createReportUpdate.mock.calls[0][4].distributedBy.value.$set).toEqual(
    selectedOption.value
  );
  expect(spy).toHaveBeenCalledWith({content: 'change'});
});

it('should have a button to remove the distribution', () => {
  const spy = jest.fn();
  const node = shallow(<DistributedBy {...config} onChange={spy} />);

  createReportUpdate.mockReturnValue({content: 'change'});

  node.find('.removeGrouping').simulate('click');

  expect(createReportUpdate.mock.calls[0][3]).toBe('none');
  expect(spy).toHaveBeenCalledWith({content: 'change'});
});

it('should hide assignee option in cloud environment', async () => {
  getOptimizeProfile.mockReturnValueOnce('cloud');
  const node = shallow(<DistributedBy {...config} />);

  await runAllEffects();

  expect(node.find({value: 'assignee'})).not.toExist();
});

it('should filter variables based on search query', () => {
  const node = shallow(<DistributedBy {...config} variables={[{name: 'a'}, {name: 'b'}]} />);

  expect(node.find({value: 'variable_a'})).toExist();
  expect(node.find({value: 'variable_b'})).toExist();

  node.find(Input).simulate('change', {target: {value: 'b'}});

  expect(node.find({value: 'variable_a'})).not.toExist();
  expect(node.find({value: 'variable_b'})).toExist();
});

it('should show the selected variable option in a hidden state if it was filtered by search', () => {
  reportConfig.process.distribution = [
    {
      key: 'none',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('None'),
    },
    {
      key: 'variable',
      matcher: jest.fn().mockReturnValue(true),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Variable'),
    },
  ];

  const node = shallow(
    <DistributedBy
      {...config}
      report={{
        ...config.report,
        distributedBy: {value: {name: 'a'}},
      }}
      variables={[{name: 'a'}, {name: 'b'}]}
    />
  );

  node.find(Input).simulate('change', {target: {value: 'b'}});

  expect(node.find('.hidden').prop('value')).toBe('variable_a');
  expect(node.find({value: 'variable_b'})).toExist();
});
