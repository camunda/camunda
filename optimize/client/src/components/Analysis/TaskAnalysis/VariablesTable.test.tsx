/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import VariablesTable from './VariablesTable';
import {OutlierNode, loadCommonOutliersVariables} from './service';

const selectedOutlierNode = {
  name: 'test',
  higherOutlier: {
    boundValue: 23,
  },
} as OutlierNode;

const props = {
  config: {
    processDefinitionKey: '',
    processDefinitionVersions: [],
    tenantIds: [],
    filters: [],
  },
  selectedOutlierNode,
};

jest.mock('./service', () => ({
  loadCommonOutliersVariables: jest.fn().mockReturnValue([
    {
      variableName: 'department',
      variableTerm: 'engineering',
      instanceCount: 50,
      outlierRatio: 0.1,
      nonOutlierRatio: 0.05,
      outlierToAllInstancesRatio: 0.01,
    },
  ]),
}));

it('should Load common outliers variables on mount', async () => {
  shallow(<VariablesTable {...props} />);
  runLastEffect();

  expect(loadCommonOutliersVariables).toHaveBeenCalled();
});

it('should render a table with correct data', async () => {
  const node = shallow(<VariablesTable {...props} />);
  runLastEffect();
  await node.update();

  const tableBody = node.find('Table').prop<(string | JSX.Element)[][]>('body');

  expect(tableBody.length).toBe(1);
  expect(tableBody[0]?.[0]).toEqual('department=engineering');
  expect(tableBody[0]?.[1]).toContain('50');
  expect(tableBody[0]?.[2]).toEqual('1');
  expect(tableBody[0]?.[3]).toEqual('10');
});

it('should render a loading indicator while loading the data', async () => {
  const node = shallow(<VariablesTable {...props} />);

  expect(node.find('Table').prop('loading')).toEqual(true);
});
