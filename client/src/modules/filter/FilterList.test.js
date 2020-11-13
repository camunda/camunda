/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import FilterList from './FilterList';

import {shallow} from 'enzyme';

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    formatters: {
      camelCaseToLabel: (text) =>
        text.replace(/([A-Z])/g, ' $1').replace(/^./, (str) => str.toUpperCase()),
    },
  };
});

it('should render an unordered list', () => {
  const node = shallow(<FilterList data={[]} />);

  expect(node.find('ul')).toExist();
});

it('should display date preview if the filter is a date filter', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'startDate',
      data: {
        type: 'fixed',
        start: startDate,
        end: endDate,
      },
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);
  expect(node).toMatchSnapshot();
});

it('should use the variables prop to resolve variable names', () => {
  const data = [
    {
      type: 'inputVariable',
      data: {
        name: 'notANameButAnId',
        type: 'String',
        data: {
          operator: 'in',
          values: ['varValue'],
        },
      },
    },
  ];

  const node = shallow(
    <FilterList variables={{inputVariable: []}} data={data} openEditFilterModal={jest.fn()} />
  );

  expect(node.find('VariablePreview').prop('variableName')).toBe('notANameButAnId');

  node.setProps({
    variables: {
      inputVariable: [{id: 'notANameButAnId', name: 'Resolved Name', type: 'String'}],
    },
  });

  expect(node.find('VariablePreview').prop('variableName')).toBe('Resolved Name');
});

it('should use the DateFilterPreview component for date variables', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'variable',
      data: {
        name: 'aDateVar',
        type: 'Date',
        data: {
          type: 'fixed',
          start: startDate,
          end: endDate,
        },
      },
    },
  ];
  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').find('DateFilterPreview')).toExist();
});

it('should display nodeListPreview for flow node filter', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode'],
      },
    },
  ];

  const node = shallow(
    <FilterList
      id={'qwe'}
      data={data}
      openEditFilterModal={jest.fn()}
      flowNodeNames={{flowNode: 'flow node name'}}
    />
  );

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'flowNode', name: 'flow node name'}],
    operator: 'in',
    type: 'executedFlowNodes',
  });
});

it('should display a flow node filter with executing nodes', () => {
  const data = [
    {
      type: 'executingFlowNodes',
      data: {
        values: ['flowNode1'],
      },
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

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
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').dive()).toIncludeText('Duration is less than 18 hours');
});

it('should display a flow node duration filter', () => {
  const data = [
    {
      type: 'flowNodeDuration',
      data: {
        a: {operator: '<', value: 18, unit: 'hours'},
      },
    },
  ];

  const node = shallow(
    <FilterList data={data} openEditFilterModal={jest.fn()} flowNodeNames={{a: 'flow node name'}} />
  );

  const actionItem = node.find('ActionItem').dive();
  expect(actionItem).toIncludeText('Duration filter is applied to 1 Flow Node');
  expect(actionItem).toIncludeText('flow node name is less than 18 hours');
});

it('should display a running instances only filter', () => {
  const data = [
    {
      type: 'runningInstancesOnly',
      data: null,
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} a />);

  expect(node.find('ActionItem').dive()).toIncludeText('Running Instances Only');
});

it('should display a completed instances only filter', () => {
  const data = [
    {
      type: 'completedInstancesOnly',
      data: null,
    },
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} a />);

  expect(node.find('ActionItem').dive()).toIncludeText('Completed Instances Only');
});
