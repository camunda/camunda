/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import FilterList from './FilterList';

import {mount, shallow} from 'enzyme';

jest.mock('components', () => {
  return {
    ActionItem: props => <button {...props}>{props.children}</button>
  };
});

jest.mock('services', () => {
  return {
    formatters: {
      camelCaseToLabel: text =>
        text.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())
    }
  };
});

it('should render an unordered list', () => {
  const node = mount(<FilterList data={[]} />);

  expect(node.find('ul')).toExist();
});

it('should display a start date filter', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'startDate',
      data: {
        type: 'fixed',
        start: startDate,
        end: endDate
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('li').length).toBe(1);
  expect(node).toIncludeText('2017-11-16');
  expect(node).toIncludeText('2017-11-26');
});

it('should display "and" between filter entries', () => {
  const data = [{type: 'completedInstancesOnly'}, {type: 'canceledInstancesOnly'}];

  const node = mount(<FilterList data={data} />);

  expect(node).toIncludeText('and');
});

it('should display a simple variable filter', () => {
  const data = [
    {
      type: 'variable',
      data: {
        name: 'varName',
        type: 'String',
        data: {
          operator: 'in',
          values: ['varValue']
        }
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('varName is varValue');
});

it('should display a variable filter with filter for undefined values selected', () => {
  const data = [
    {
      type: 'variable',
      data: {
        name: 'varName',
        type: 'String',
        filterForUndefined: true,
        data: {}
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('varName is null or undefined');
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
          values: ['varValue']
        }
      }
    }
  ];

  const node = shallow(
    <FilterList
      variables={{inputVariable: [{id: 'notANameButAnId', name: 'Resolved Name', type: 'String'}]}}
      data={data}
      openEditFilterModal={jest.fn()}
    />
  );

  expect(node.find('ActionItem span').first()).toIncludeText('Resolved Name');
});

it('should display a date variable filter as a range', () => {
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
          end: endDate
        }
      }
    }
  ];
  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('aDateVar is between 2017-11-16 and 2017-11-26');
});

it('should combine multiple variable values with or', () => {
  const data = [
    {
      type: 'variable',
      data: {
        name: 'varName',
        type: 'String',
        data: {
          operator: 'in',
          values: ['varValue', 'varValue2']
        }
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('varName is varValue or varValue2');
});

it('should combine multiple variable names with neither/nor for the not in operator', () => {
  const data = [
    {
      type: 'variable',
      data: {
        name: 'varName',
        type: 'String',
        data: {
          operator: 'not in',
          values: ['varValue', 'varValue2']
        }
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('varName is neither varValue nor varValue2');
});

it('should display a simple flow node filter', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode']
      }
    }
  ];

  const node = mount(<FilterList id={'qwe'} data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('Executed Flow Node is flowNode');
});

it('should display flow node name instead of id', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode']
      }
    }
  ];

  const node = mount(
    <FilterList
      id={'qwe'}
      processDefinitionKey="aKey"
      processDefinitionVersion="1"
      data={data}
      openEditFilterModal={jest.fn()}
      flowNodeNames={{flowNode: 'flow Node hello'}}
    />
  );

  expect(node).toIncludeText('Executed Flow Node is flow Node hello');
});

it('should display the node id if the name is null for Exectuted flow Node', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode']
      }
    }
  ];

  const node = await mount(<FilterList id={'qwe'} data={data} openEditFilterModal={jest.fn()} />);
  node.setState({flowNodeNames: {flowNode: null}});

  expect(node).toIncludeText('Executed Flow Node is flowNode');
});

it('should display a flow node filter with multiple selected nodes', () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode1', 'flowNode2']
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('Executed Flow Node is flowNode1 or flowNode2');
});

it('should display a flow node filter with non-executed nodes', () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'not in',
        values: ['flowNode1', 'flowNode2']
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('Executed Flow Node is neither flowNode1 nor flowNode2');
});

it('should display a rolling date filter', () => {
  const data = [
    {
      type: 'startDate',
      data: {
        type: 'relative',
        start: {
          value: 18,
          unit: 'hours'
        },
        end: null
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('Start Date is less than 18 hours ago');
});

it('should display an end date filter', () => {
  const startDate = '2017-11-16T00:00:00';
  const endDate = '2017-11-26T23:59:59';
  const data = [
    {
      type: 'endDate',
      data: {
        type: 'fixed',
        start: startDate,
        end: endDate
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('End Date is between');
});

it('should display a duration filter', () => {
  const data = [
    {
      type: 'processInstanceDuration',
      data: {
        operator: '<',
        value: 18,
        unit: 'hours'
      }
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node).toIncludeText('Duration is less than 18 hours');
});

it('should display a running instances only filter', () => {
  const data = [
    {
      type: 'runningInstancesOnly',
      data: null
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} a />);

  expect(node).toIncludeText('Running Process Instances Only');
});

it('should display a completed instances only filter', () => {
  const data = [
    {
      type: 'completedInstancesOnly',
      data: null
    }
  ];

  const node = mount(<FilterList data={data} openEditFilterModal={jest.fn()} a />);

  expect(node).toIncludeText('Completed Process Instances Only');
});
