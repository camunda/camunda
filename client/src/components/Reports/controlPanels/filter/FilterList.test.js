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
      camelCaseToLabel: text =>
        text.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase())
    }
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
        end: endDate
      }
    }
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);
  expect(node).toMatchSnapshot();
});

it('should display "and" between filter entries', () => {
  const data = [{type: 'completedInstancesOnly'}, {type: 'canceledInstancesOnly'}];

  const node = shallow(<FilterList data={data} />);

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

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').dive()).toIncludeText('varName is varValue');
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

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').dive()).toIncludeText('varName is null or undefined');
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
    <FilterList variables={{inputVariable: []}} data={data} openEditFilterModal={jest.fn()} />
  );

  expect(node.find('ActionItem span').first()).toIncludeText('notANameButAnId');

  node.setProps({
    variables: {
      inputVariable: [{id: 'notANameButAnId', name: 'Resolved Name', type: 'String'}]
    }
  });

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
  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').dive()).toIncludeText(
    'aDateVar is between 2017-11-16 and 2017-11-26'
  );
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

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').dive()).toIncludeText('varName is varValue or varValue2');
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

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').dive()).toIncludeText('varName is neither varValue nor varValue2');
});

it('should display nodeListPreview for flow node filter', async () => {
  const data = [
    {
      type: 'executedFlowNodes',
      data: {
        operator: 'in',
        values: ['flowNode']
      }
    }
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
    operator: 'in'
  });
});

it('should display a flow node filter with executing nodes', () => {
  const data = [
    {
      type: 'executingFlowNodes',
      data: {
        values: ['flowNode1']
      }
    }
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'flowNode1', name: undefined}],
    operator: undefined
  });
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

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} />);

  expect(node.find('ActionItem').dive()).toIncludeText('Duration is less than 18 hours');
});

it('should display a running instances only filter', () => {
  const data = [
    {
      type: 'runningInstancesOnly',
      data: null
    }
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} a />);

  expect(node.find('ActionItem').dive()).toIncludeText('Running Process Instances Only');
});

it('should display a completed instances only filter', () => {
  const data = [
    {
      type: 'completedInstancesOnly',
      data: null
    }
  ];

  const node = shallow(<FilterList data={data} openEditFilterModal={jest.fn()} a />);

  expect(node.find('ActionItem').dive()).toIncludeText('Completed Process Instances Only');
});
