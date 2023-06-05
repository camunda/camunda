/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import update from 'immutability-helper';

import {loadVariables, loadInputVariables, loadOutputVariables} from 'services';

import {InstanceCount} from './InstanceCount';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadInputVariables: jest
      .fn()
      .mockReturnValue([{id: 'input1', name: 'Input 1', type: 'String'}]),
    loadOutputVariables: jest
      .fn()
      .mockReturnValue([{id: 'output1', name: 'Output 1', type: 'String'}]),
    loadVariables: jest.fn().mockReturnValue([{name: 'variable1', type: 'String'}]),
  };
});

beforeEach(() => {
  loadInputVariables.mockClear();
  loadOutputVariables.mockClear();
  loadVariables.mockClear();
});

const props = {
  report: {
    data: {
      filter: [{type: 'runningInstancesOnly'}],
      definitions: [
        {
          key: 'aKey',
          versions: ['1'],
          tenantIds: ['tenantId'],
        },
      ],
    },
    result: {
      instanceCount: 123,
      instanceCountWithoutFilters: 500,
    },
    reportType: 'process',
  },
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should should show the instance count', () => {
  const node = shallow(<InstanceCount {...props} />);

  expect(node).toIncludeText('123');
});

it('should not show the total instance count if there are no filters', () => {
  const noFilterReport = update(props.report, {data: {filter: {$set: []}}});
  const node = shallow(<InstanceCount {...props} report={noFilterReport} />);

  expect(node).not.toIncludeText('500');
});

it('should show the total instance count if there are filters', () => {
  const node = shallow(<InstanceCount {...props} />);

  expect(node).toIncludeText('500');
});

it('should display an astrick near the total instance count if the report includes date filters', () => {
  const node = shallow(
    <InstanceCount
      {...props}
      report={update(props.report, {
        data: {filter: {$set: [{type: 'instanceStartDate'}]}},
      })}
    />
  );

  expect(node).toIncludeText('Displaying data from 123 of *500 instances');
});

it('should contain a popover with information about the filters', () => {
  const node = shallow(<InstanceCount {...props} />);

  expect(node.find('.instanceCountPopover')).toExist();
  expect(node.find('FilterList').prop('data')).toEqual(props.report.data.filter);
});

it('should separate report and dashboard level filters', () => {
  const allFilters = [
    {type: 'runningInstancesOnly', data: null},
    {
      type: 'processInstanceDuration',
      data: {value: 7, unit: 'days', operator: '>', includeNull: false},
    },
    {type: 'runningInstancesOnly', data: null},
    {type: 'nonCanceledInstancesOnly', data: null},
  ];

  const dashboardFilters = [
    {type: 'runningInstancesOnly', data: null},
    {type: 'nonCanceledInstancesOnly', data: null},
  ];

  const reportWithAllFilters = update(props.report, {data: {filter: {$set: allFilters}}});

  const node = shallow(
    <InstanceCount {...props} report={reportWithAllFilters} additionalFilter={dashboardFilters} />
  );

  expect(node.find('FilterList').at(0).prop('data')).toMatchSnapshot();
  expect(node.find('FilterList').at(1).prop('data')).toMatchSnapshot();
});

it('should not contain a popover if the report has no filters', () => {
  const noFilterReport = update(props.report, {data: {filter: {$set: []}}});
  const node = shallow(<InstanceCount {...props} report={noFilterReport} />);

  expect(node.find('.instanceCountPopover')).not.toExist();
});

it('should disable the popover if the noInfo prop is set', () => {
  const node = shallow(<InstanceCount {...props} noInfo />);

  expect(node.find('.instanceCountPopover').prop('disabled')).toBe(true);
});

it('should load variable names for process reports', async () => {
  const node = shallow(<InstanceCount {...props} />);

  node.find('span').first().simulate('click');

  await flushPromises();

  const payload = [
    {
      processDefinitionKey: 'aKey',
      processDefinitionVersions: ['1'],
      tenantIds: ['tenantId'],
    },
  ];

  expect(loadVariables).toHaveBeenCalledWith(payload);
  expect(node.find('FilterList').prop('variables')).toEqual([{name: 'variable1', type: 'String'}]);
});

it('should not load variables if definition is incomplete', async () => {
  const node = shallow(
    <InstanceCount {...props} report={update(props.report, {data: {definitions: {$set: []}}})} />
  );

  node.find('span').first().simulate('click');

  await flushPromises();

  expect(loadVariables).not.toHaveBeenCalled();
});

it('should load variable names for decision reports', async () => {
  const decisionReport = update(props.report, {
    reportType: {$set: 'decision'},
  });
  const node = shallow(
    <InstanceCount
      {...props}
      report={decisionReport}
      mightFail={async (data, cb) => cb(await data)}
    />
  );

  node.find('span').first().simulate('click');

  await flushPromises();

  const payload = [
    {
      decisionDefinitionKey: 'aKey',
      decisionDefinitionVersions: ['1'],
      tenantIds: ['tenantId'],
    },
  ];

  expect(loadInputVariables).toHaveBeenCalledWith(payload);
  expect(loadOutputVariables).toHaveBeenCalledWith(payload);
  expect(node.find('FilterList').prop('variables')).toMatchSnapshot();
});

it('should substitute the popover title with an icon if requested', () => {
  const node = shallow(<InstanceCount {...props} useIcon="someIcon" />);

  expect(node.find('.instanceCountPopover').prop('title')).toBe(false);
  expect(node.find('.instanceCountPopover').prop('icon')).toBe('someIcon');
});

it('should show instance count and filter list headings if showHeader prop is added', () => {
  const node = shallow(<InstanceCount {...props} showHeader />);

  expect(node.find('.instanceCountPopover .countString')).toExist();
  expect(node.find('.filterListHeading')).toExist();
});
