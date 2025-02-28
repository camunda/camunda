/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import OutlierDetailsTable from './OutlierDetailsTable';
import {Button} from '@carbon/react';

const props = {
  flowNodeNames: {
    task1: 'Task 1',
  },
  nodeOutliers: {
    task1: {
      totalCount: 10,
      higherOutlier: {count: 1, relation: 0.5, boundValue: 0},
    },
    task2: {
      totalCount: 5,
      higherOutlier: {count: 2, relation: 0.1, boundValue: 0},
    },
  },
  outlierVariables: {
    task1: [{variableName: 'variable1', variableTerm: true}],
  },
  config: {filters: [], tenantIds: [], processDefinitionKey: '', processDefinitionVersions: []},
  onDetailsClick: jest.fn(),
};

it('should render the table properly', () => {
  const node = shallow(<OutlierDetailsTable {...props} />);

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');

  expect(tableBody.length).toBe(2);
  expect(tableBody[0]?.length).toBe(5);

  // task 1 with name and variable
  expect(tableBody[0]?.[0]).toBe('Task 1');
  expect(tableBody[0]?.[1]).toBe('10');
  expect(tableBody[0]?.[2]).toBe('1 instance took 50% longer than average.');
  const task1ViewDetails = shallow(<div>{tableBody[0]?.[3]}</div>);
  expect(task1ViewDetails.text()).toBe('View details');
  expect(task1ViewDetails.find(Button).text()).toBe('View details');
  expect(task1ViewDetails.find(Button)).toExist();

  // task 2 with no additional data
  expect(tableBody[1]?.[0]).toBe('task2');
  expect(tableBody[1]?.[1]).toBe('5');
  expect(tableBody[1]?.[2]).toBe('2 instances took 10% longer than average.');
  const task2ViewDetails = shallow(<div>{tableBody[1]?.[3]}</div>);
  expect(task2ViewDetails.text()).toBe('View details');
  expect(task2ViewDetails.find(Button).text()).toBe('View details');
  expect(task2ViewDetails.find(Button)).toExist();
});

it('should ommit tasks without data', () => {
  const node = shallow(
    <OutlierDetailsTable {...props} nodeOutliers={{...props.nodeOutliers, task: undefined}} />
  );

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');

  expect(tableBody.length).toBe(2);
});

it('should ommit tasks without higher outlier', () => {
  const node = shallow(
    <OutlierDetailsTable
      {...props}
      nodeOutliers={{...props.nodeOutliers, task: {totalCount: 10}}}
    />
  );

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');

  expect(tableBody.length).toBe(2);
});

it('should render download instances button', () => {
  const node = shallow(<OutlierDetailsTable {...props} />);

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');
  const downloadButton = tableBody[0]?.[4] as JSX.Element;

  expect(downloadButton.props).toMatchObject({
    id: 'task1',
    name: 'Task 1',
    totalCount: 10,
    value: 0,
  });
});
