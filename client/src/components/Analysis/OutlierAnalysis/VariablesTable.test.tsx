/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {VariablesTable} from './VariablesTable';
import {SelectedNode, loadCommonOutliersVariables} from './service';

const selectedNode = {
  name: 'test',
  higherOutlier: {
    boundValue: 23,
  },
} as SelectedNode;

const props = {
  config: {
    processDefinitionKey: '',
    processDefinitionVersions: [],
    tenantIds: [],
  },
  getUser: jest.fn(),
  refreshUser: jest.fn(),
  totalCount: 0,
  selectedNode: selectedNode,
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
  getInstancesDownloadUrl: jest.fn(),
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

  expect(node).toMatchSnapshot();
});

it('should render a loading indicator while loading the data', async () => {
  const node = shallow(<VariablesTable {...props} />);

  expect(node).toMatchSnapshot();
});
