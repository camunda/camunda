/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ComponentProps} from 'react';
import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {loadProcessDefinitionXml} from 'services';
import {ClickBehavior} from 'components';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import {NodeDateFilter} from './NodeDateFilter';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadProcessDefinitionXml: jest.fn().mockReturnValue('fooXml'),
}));

beforeEach(() => {
  (loadProcessDefinitionXml as jest.Mock).mockClear();
});

const props: ComponentProps<typeof NodeDateFilter> = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  definitions: [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
  ],
  filterType: 'flowNodeStartDate',
  filterLevel: 'instance',
  close: jest.fn(),
  addFilter: jest.fn(),
};

it('should contain a modal', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should display a diagram', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  runAllEffects();
  runAllEffects();

  expect(node.find('.diagramContainer').childAt(0).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();
  runAllEffects();

  node.find(ClickBehavior).prop('onClick')(flowNode);

  expect(node.find(ClickBehavior).prop('selectedNodes')).toContain('bar');
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = shallow(<NodeDateFilter {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();
  runAllEffects();

  node.find(ClickBehavior).prop('onClick')(flowNode);
  node.find(ClickBehavior).prop('onClick')(flowNode);

  expect(node.find(ClickBehavior).prop('selectedNodes')).not.toContain('bar');
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(
    <NodeDateFilter {...props} filterData={{appliedTo: [], data: {flowNodeIds: []}, type: ''}} />
  );

  runAllEffects();
  runAllEffects();

  expect(node.find('.confirm').prop('disabled')).toBeTruthy(); // create filter
});

it('should load new xml and reset selected nodes after changing definition', () => {
  const definitions = [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
    {
      identifier: 'otherDefinition',
      key: 'otherDefinitionKey',
      versions: ['1'],
      tenantIds: ['marketing', 'sales'],
    },
  ];
  const node = shallow(<NodeDateFilter {...props} definitions={definitions} />);

  runAllEffects();
  runAllEffects();

  node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);
  node.find(ClickBehavior).prop('onClick')({
    name: 'foo',
    id: 'bar',
  });

  runAllEffects();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('otherDefinitionKey', '1', 'marketing');
  expect(node.find(ClickBehavior).prop('selectedNodes')?.length).toBe(0);
});
