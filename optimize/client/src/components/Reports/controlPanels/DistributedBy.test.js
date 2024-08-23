/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {Select} from 'components';
import {reportConfig, createReportUpdate} from 'services';
import {useUiConfig} from 'hooks';

import DistributedBy from './DistributedBy';

jest.mock('hooks', () => ({
  useUiConfig: jest.fn().mockReturnValue({userTaskAssigneeAnalyticsEnabled: true}),
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
  reportConfig.distribution = [
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
  reportConfig.distribution[1].enabled.mockReturnValue(false);

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

  const CarbonselectedOption = {
    type: 'variable',
    value: {id: 'test', name: 'testName', type: 'date'},
  };

  createReportUpdate.mockReturnValue({content: 'change'});

  node.find(Select).simulate('change', 'variable_testName');

  expect(createReportUpdate.mock.calls[0][3].distributedBy.value.$set).toEqual(
    CarbonselectedOption.value
  );
  expect(spy).toHaveBeenCalledWith({content: 'change'});
});

it('should have a button to remove the distribution', () => {
  const spy = jest.fn();
  const node = shallow(<DistributedBy {...config} onChange={spy} />);

  createReportUpdate.mockReturnValue({content: 'change'});

  node.find('.removeGrouping').simulate('click');

  expect(createReportUpdate.mock.calls[0][2]).toBe('none');
  expect(spy).toHaveBeenCalledWith({content: 'change'});
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
    <DistributedBy
      {...config}
      report={{
        ...config.report,
        groupBy: {value: {name: 'a'}},
      }}
      variables={null}
    />
  );

  expect(node.find({label: 'Variable'}).prop('disabled')).toBe(true);
});

it('should hide assignee option when assignee analytics are disabled', async () => {
  useUiConfig.mockImplementation(() => ({userTaskAssigneeAnalyticsEnabled: false}));
  const node = shallow(<DistributedBy {...config} />);

  await runAllEffects();

  expect(node.find({value: 'assignee'})).not.toExist();
});
