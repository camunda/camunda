/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';
import OutlierDetailsTable from './OutlierDetailsTable';

const props = {
  flowNodeNames: {
    task1: 'Task 1',
  },
  tasksData: {
    task1: {
      totalCount: 10,
      higherOutlier: {count: 1, relation: 0.5},
    },
    task2: {
      totalCount: 5,
      higherOutlier: {count: 2, relation: 0.1},
    },
  },
  outlierVariables: {
    task1: [{variableName: 'variable1', variableTerm: true}],
  },
  onDetailsClick: jest.fn(),
};

it('should render the table properly', () => {
  const node = shallow(<OutlierDetailsTable {...props} />);

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');

  // task 1 with name and variable
  expect(tableBody[0]?.[0]).toBe('Task 1');
  expect(tableBody[0]?.[1]).toBe('10');
  expect(tableBody[0]?.[2]).toBe('1 instance took 50% longer than average.');
  const task1Variabes = shallow(tableBody[0]?.[3] as JSX.Element);
  expect(task1Variabes.text()).toBe('variable1=true');

  // task 2 with no additional data
  expect(tableBody[1]?.[0]).toBe('task2');
  expect(tableBody[1]?.[1]).toBe('5');
  expect(tableBody[1]?.[2]).toBe('2 instances took 10% longer than average.');
  expect(tableBody[1]?.[3]).toBe('-');
});

it('should ommit tasks without data', () => {
  const node = shallow(
    <OutlierDetailsTable {...props} tasksData={{...props.tasksData, task: undefined}} />
  );

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');

  expect(tableBody.length).toBe(2);
});

it('should ommit tasks without higher outlier', () => {
  const node = shallow(
    <OutlierDetailsTable {...props} tasksData={{...props.tasksData, task: {totalCount: 10}}} />
  );

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');

  expect(tableBody.length).toBe(2);
});
