/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import FlowNodeResolver from './FlowNodeResolver';
import FilterList from './FilterList';

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
  expect(node).toMatchSnapshot();
});

it('should display date preview for decision date time filter', () => {
  const data = [
    {
      type: 'evaluationDateTime',
      data: {
        type: 'relative',
        value: 0,
        unit: 'days',
      },
    },
  ];

  const node = shallow(<FilterList data={data} />);
  expect(node.find('DateFilterPreview')).toExist();
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
      appliedTo: ['definition'],
    },
  ];

  const node = shallow(<FilterList {...props} variables={{inputVariable: []}} data={data} />);

  expect(node.find('VariablePreview').prop('variableName')).toBe('Missing variable');

  node.setProps({
    variables: {
      inputVariable: [{id: 'notANameButAnId', name: 'Resolved Name', type: 'String'}],
    },
  });

  expect(node.find('VariablePreview').prop('variableName')).toBe('Resolved Name');

  node.setProps({
    variables: {inputVariable: [{id: 'notANameButAnId', name: null, type: 'String'}]},
  });

  expect(node.find('VariablePreview').prop('variableName')).toBe('notANameButAnId');
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
  expect(actionItem.prop('onEdit')).toEqual(undefined);
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

it('should display excluded flow nodes for view level flow node selection filter', () => {
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

  expect(node.find('.parameterName')).toIncludeText('Flow Node Selection');
  expect(node.find('.filterText')).toIncludeText('2 excluded Flow Nodes');
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

  expect(node).toMatchSnapshot();
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

  expect(actionItem).toIncludeText('Duration is less than');
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

  expect(node).toMatchSnapshot();
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

  expect(node).toMatchSnapshot();
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

  expect(node.find('.filterText').prop('dangerouslySetInnerHTML').__html).toBe(
    '<b>Running</b> Instances only'
  );
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

  expect(node.find('.filterText').prop('dangerouslySetInnerHTML').__html).toBe(
    '<b>Completed</b> Instances only'
  );
});

describe('apply to handling', () => {
  const data = [
    {
      type: 'completedInstancesOnly',
      data: null,
      appliedTo: ['definition1'],
    },
  ];

  it('should show how many definitions the filter applies to', () => {
    const node = shallow(
      <FilterList
        data={data}
        definitions={[{identifier: 'definition1'}, {identifier: 'definition2'}]}
      />
    );

    expect(node.find('.appliedTo')).toIncludeText('Applied to: 1 Process');
  });

  it('should not show how many definitions the filter applies to if there is only one definition', () => {
    const node = shallow(<FilterList data={data} definitions={[{identifier: 'definition1'}]} />);

    expect(node.find('.appliedTo')).not.toExist();
  });
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
