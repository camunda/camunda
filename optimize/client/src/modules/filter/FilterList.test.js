/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {Tag} from '@carbon/react';

import FlowNodeResolver from './FlowNodeResolver';
import FilterList from './FilterList';
import {DateFilterPreview} from './modals';
import {ActionItem} from 'components';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    formatters: {
      camelCaseToLabel: (text) =>
        text.replace(/([A-Z])/g, ' $1').replace(/^./, (str) => str.toUpperCase()),
    },
  };
});

const props = {
  definitions: [
    {
      identifier: 'definition',
      versions: ['all'],
      tenantIds: [null],
    },
  ],
  openEditFilterModal: () => {},
  deleteFilter: () => {},
};

it('should render an unordered list', () => {
  const node = shallow(<FilterList data={[]} />);

  expect(node.find('ul')).toExist();
});

it('should display date preview if the filter is a date filter', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'instanceStartDate',
      data: {
        type: 'fixed',
        start: startDate,
        end: endDate,
      },
    },
  ];

  const node = shallow(<FilterList data={data} />);
  const dateFilterPreview = node.find(DateFilterPreview);

  expect(dateFilterPreview.prop('filter').start).toBe(startDate);
  expect(dateFilterPreview.prop('filter').end).toBe(endDate);
});

it('should use the variables prop to resolve variable names', () => {
  const data = [
    {
      type: 'variables',
      data: {name: 'variableName', type: 'String'},
      appliedTo: ['definition'],
    },
  ];

  const node = shallow(<FilterList {...props} variables={[]} data={data} />);

  expect(node.find('VariablePreview').prop('variableName')).toBe('Missing variable');

  node.setProps({
    variables: null,
  });

  expect(node.find('VariablePreview').prop('variableName')).toBe('variableName');
});

it('should disable editing and pass a warning to variablePreview if variable does not exist', () => {
  const data = [
    {
      type: 'multipleVariable',
      data: {
        data: [
          {
            name: 'notANameButAnId',
            type: 'String',
            data: {
              operator: 'in',
              values: ['varValue'],
            },
          },
        ],
      },
      appliedTo: ['definition'],
    },
  ];

  const node = shallow(<FilterList {...props} data={data} variables={[]} />);

  const actionItem = node.find('ActionItem');

  expect(actionItem.prop('warning')).toBe('Variable does not exist');
});

it('should show variable id if variables are not loaded yet', () => {
  const variableName = 'notANameButAnId';
  const data = [
    {
      type: 'multipleVariable',
      data: {data: [{name: variableName, data: {}}]},
      appliedTo: ['definition'],
    },
  ];

  const node = shallow(<FilterList {...props} data={data} variables={null} />);

  const variablePreview = node.find('ActionItem').find('VariablePreview');
  expect(variablePreview.prop('variableName')).toBe(variableName);
});

it('should use the DateFilterPreview component for date variables and variable preview for other types', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'multipleVariable',
      data: {
        data: [
          {
            name: 'aDateVar',
            type: 'Date',
            data: {
              type: 'fixed',
              start: startDate,
              end: endDate,
            },
          },
          {
            name: 'notANameButAnId',
            type: 'String',
            data: {
              operator: 'in',
              values: ['varValue'],
            },
          },
        ],
      },
      appliedTo: ['definition'],
    },
  ];
  const node = shallow(<FilterList {...props} data={data} />);

  expect(node.find('ActionItem').find('DateFilterPreview')).toExist();
  expect(node.find('ActionItem').find('VariablePreview')).toExist();
});

it('should display nodeListPreview for flow node filter', () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode'],
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);

  node = shallow(node.find(FlowNodeResolver).prop('render')({flowNode: 'flow node name'}));

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'flowNode', name: 'flow node name'}],
    operator: 'in',
    type: 'executedFlowNodes',
  });
});

it('should display excluded flow nodes for flow node selection filter with not in operator', () => {
  const data = [
    {
      type: 'executedFlowNodes',
      filterLevel: 'view',
      data: {
        operator: 'no in',
        values: ['flowNode1', 'flownode2'],
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({flowNode: 'flow node name'}));

  expect(node.find('.parameterName')).toIncludeText('Flow node selection');
  expect(node.find('.filterText')).toIncludeText('2 excluded flow node(s)');
});

it('should display included flow nodes for flow node selection filter with in operator', () => {
  const data = [
    {
      type: 'executedFlowNodes',
      filterLevel: 'view',
      data: {
        operator: 'in',
        values: ['flowNode1'],
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({flowNode: 'flow node name'}));

  expect(node.find('.parameterName')).toIncludeText('Flow node selection');
  expect(node.find('.filterText')).toIncludeText('1 selected flow node(s)');
});

it('should disable editing and pass a warning to the filter item if at least one flow node does not exist', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNodeThatDoesNotExist'],
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({}));

  expect(node.find(ActionItem).prop('warning')).toBe('Flow node(s) does not exist');
});

it('should display a flow node filter with executing nodes', () => {
  const data = [
    {
      type: 'executingFlowNodes',
      data: {
        values: ['flowNode1'],
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({}));

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'flowNode1', name: undefined}],
    operator: undefined,
    type: 'executingFlowNodes',
  });
});

it('should display a duration filter', () => {
  const data = [
    {
      type: 'processInstanceDuration',
      data: {
        operator: '<',
        value: 18,
        unit: 'hours',
      },
      appliedTo: ['definition'],
    },
  ];

  const node = shallow(<FilterList {...props} data={data} />);
  const actionItem = node.find('ActionItem').dive();

  expect(actionItem).toIncludeText('is less than');
  expect(actionItem.find(Tag)).toIncludeText('duration');
  expect(actionItem.find('b').prop('children').join('')).toBe('18 hours');
});

it('should display a flow node duration filter', () => {
  const data = [
    {
      type: 'flowNodeDuration',
      data: {
        a: {operator: '<', value: 18, unit: 'hours'},
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({a: 'flow node name'}));

  expect(node.find(ActionItem).dive().find(Tag)).toIncludeText('Flow node duration');
});

it('should show flow node duration filter in expanded state if specified', () => {
  const data = [
    {
      type: 'flowNodeDuration',
      data: {
        a: {operator: '<', value: 18, unit: 'hours'},
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} expanded />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({a: 'flow node name'}));

  expect(node.find('b')).toExist();
});

it('should disable editing and pass a warning to the filter item if at least one flow node does not exist', async () => {
  const data = [
    {
      type: 'flowNodeDuration',
      data: {
        flowNodeThatDoesNotExist: {operator: '<', value: 18, unit: 'hours'},
      },
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({}));

  expect(node.find(ActionItem).prop('warning')).toBe('Flow node(s) does not exist');
});

it('should display a running instances only filter', () => {
  const data = [
    {
      type: 'runningInstancesOnly',
      data: null,
      appliedTo: ['definition'],
    },
  ];

  const node = shallow(<FilterList {...props} data={data} />);

  expect(node.find('.filterText').text()).toBe('Running Instances only');
});

it('should display a completed instances only filter', () => {
  const data = [
    {
      type: 'completedInstancesOnly',
      data: null,
      appliedTo: ['definition'],
    },
  ];

  const node = shallow(<FilterList {...props} data={data} />);

  expect(node.find('.filterText').text()).toBe('Completed Instances only');
});

it('should display node date filter preview', () => {
  const filterData = {
    flowNodeIds: ['flowNode'],
    value: 0,
    unit: 'days',
  };

  const data = [
    {
      type: 'flowNodeStartDate',
      filterLevel: 'instance',
      data: filterData,
      appliedTo: ['definition'],
    },
  ];

  let node = shallow(<FilterList {...props} data={data} />);
  node = shallow(node.find(FlowNodeResolver).prop('render')({flowNode: 'flow node name'}));

  expect(node.find('DateFilterPreview').prop('filter')).toEqual(filterData);
  expect(node.find('.content')).toIncludeText('flow node name');
});
