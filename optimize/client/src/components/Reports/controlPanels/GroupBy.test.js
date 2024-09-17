/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {Button} from '@carbon/react';

import {Select} from 'components';
import {reportConfig, createReportUpdate} from 'services';
import {useUiConfig} from 'hooks';

import GroupBy from './GroupBy';

jest.mock('hooks', () => ({
  useUiConfig: jest
    .fn()
    .mockReturnValue({optimizeProfile: 'ccsm', userTaskAssigneeAnalyticsEnabled: true}),
}));

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    reportConfig: {
      ...rest.reportConfig,
      process: {
        group: [],
        distribution: [],
      },
    },
    createReportUpdate: jest.fn(),
  };
});

const config = {
  type: 'process',
  variables: {variable: []},
  onChange: jest.fn(),
  report: {
    view: {},
    groupBy: {type: 'group'},
    distributedBy: {type: 'distribution'},
    definitions: [{id: 'definitionId'}],
  },
};

beforeEach(() => {
  reportConfig.group = [
    {
      key: 'none',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('None'),
    },
    {
      key: 'group1',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Group 1'),
    },
    {
      key: 'group2',
      matcher: jest.fn().mockReturnValue(true),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Group 2'),
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
  reportConfig.distribution = [
    {
      key: 'none',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('None'),
    },
    {
      key: 'distribution',
      matcher: jest.fn().mockReturnValue(true),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Sample Distribution'),
    },
  ];

  createReportUpdate.mockClear();
});

it('should disable options which would create a wrong combination', () => {
  reportConfig.group[1].enabled.mockReturnValue(false);

  const node = shallow(<GroupBy {...config} />);

  expect(node.find(Select.Option).first()).toBeDisabled();
});

it('should disable the variable view submenu if there are no variables', () => {
  const node = shallow(<GroupBy {...config} />);

  expect(node.find(Select.Submenu)).toBeDisabled();
});

it('invoke configUpdate with the correct variable data', async () => {
  const spy = jest.fn();
  const node = shallow(
    <GroupBy
      {...config}
      variables={{variable: [{id: 'test', type: 'date', name: 'testName'}]}}
      onChange={spy}
    />
  );

  const SelectedOption = {
    type: 'variable',
    value: {id: 'test', name: 'testName', type: 'date'},
  };

  createReportUpdate.mockReturnValue({content: 'change'});

  node.find(Select).simulate('change', 'variable_testName');

  expect(createReportUpdate.mock.calls[0][3].groupBy.value.$set).toEqual(SelectedOption.value);
  expect(spy).toHaveBeenCalledWith({content: 'change'});
});

it('should use the distributedBy value when removing the groupBy', () => {
  const spy = jest.fn();
  const node = shallow(<GroupBy {...config} onChange={spy} />);

  node.find(Button).simulate('click');

  expect(createReportUpdate.mock.calls[0][3].groupBy.$set).toEqual({type: 'distribution'});
});

it('should pass null as a value to Select if groupBy is null', () => {
  const node = shallow(<GroupBy {...config} report={{...config.report, groupBy: null}} />);

  expect(node.find(Select).prop('value')).toBe(null);
});

it('should not fail if variables are null', () => {
  reportConfig.group = [
    {
      key: 'none',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('None'),
    },
    {
      key: 'group1',
      matcher: jest.fn().mockReturnValue(false),
      visible: jest.fn().mockReturnValue(true),
      enabled: jest.fn().mockReturnValue(true),
      label: jest.fn().mockReturnValue('Group 1'),
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
    <GroupBy
      {...config}
      report={{
        ...config.report,
        groupBy: {value: {name: 'a'}},
      }}
      variables={{variable: null}}
    />
  );

  expect(node.find({label: 'Variable'}).prop('disabled')).toBe(true);
});

it('should hide assignee option in cloud environment', async () => {
  useUiConfig.mockImplementation(() => ({userTaskAssigneeAnalyticsEnabled: false}));
  const node = shallow(<GroupBy {...config} />);

  await runAllEffects();

  expect(node.find({value: 'assignee'})).not.toExist();
});
