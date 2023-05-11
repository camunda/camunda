/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {Button, ClickBehavior} from 'components';
import {loadProcessDefinitionXml} from 'services';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';
import {NodeFilter} from './NodeFilter';
import NodeListPreview from './NodeListPreview';
import {ComponentProps} from 'react';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadProcessDefinitionXml: jest.fn().mockReturnValue('fooXml'),
}));

beforeEach(() => {
  (loadProcessDefinitionXml as jest.Mock).mockClear();
});

const props: ComponentProps<typeof NodeFilter> = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  definitions: [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
  ],
  addFilter: jest.fn(),
  close: jest.fn(),
  filterLevel: 'instance',
  filterType: 'executedFlowNodes',
};

it('should contain a modal', () => {
  const node = shallow(<NodeFilter {...props} />);

  expect(node.find('Modal')).toExist();
});

it('should display a diagram', () => {
  const node = shallow(<NodeFilter {...props} />);

  runAllEffects();

  expect(node.find('.diagramContainer').childAt(0).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = shallow(<NodeFilter {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();

  node.find(ClickBehavior).prop('onClick')(flowNode);

  expect(node.find(ClickBehavior).prop('selectedNodes')).toContain(flowNode);
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = shallow(<NodeFilter {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();

  node.find(ClickBehavior).prop('onClick')(flowNode);
  node.find(ClickBehavior).prop('onClick')(flowNode);

  expect(node.find(ClickBehavior).prop('selectedNodes')).not.toContain(flowNode);
});

it('should create an executed node filter when operator is specified', () => {
  const spy = jest.fn();
  const node = shallow(<NodeFilter {...props} addFilter={spy} />);

  const flowNode1 = {
    name: 'foo',
    id: 'bar',
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();

  node.find(ClickBehavior).prop('onClick')(flowNode1);
  node.find(ClickBehavior).prop('onClick')(flowNode2);

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'executedFlowNodes',
    data: {
      operator: 'in',
      values: [flowNode1.id, flowNode2.id],
    },
    appliedTo: ['definition'],
  });
});

it('should set filter type depending on selected operation', () => {
  const spy = jest.fn();
  const node = shallow(<NodeFilter {...props} addFilter={spy} />);

  const flowNode1 = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();

  node.find(Button).at(0).simulate('click');

  node.find(ClickBehavior).prop('onClick')?.(flowNode1);

  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'executingFlowNodes',
    data: {
      operator: undefined,
      values: [flowNode1.id],
    },
    appliedTo: ['definition'],
  });

  node.find(Button).at(3).simulate('click');
  node.find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'canceledFlowNodes',
    data: {
      operator: undefined,
      values: [flowNode1.id],
    },
    appliedTo: ['definition'],
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(<NodeFilter {...props} />);

  runAllEffects();

  expect(node.find('.confirm').prop('disabled')).toBeTruthy(); // create filter
});

it('should create preview list of selected node', () => {
  const node = shallow(<NodeFilter {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();

  node.find(ClickBehavior).prop('onClick')(flowNode);

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'bar', name: 'foo'}],
    operator: 'in',
    type: 'executedFlowNodes',
  });
});

it('should contain buttons to switch between executed and not executed mode', () => {
  const node = shallow(<NodeFilter {...props} />);

  runAllEffects();

  const buttonGroup = node.find('ButtonGroup');

  expect(buttonGroup.find(Button).at(0).text()).toBe('Running');
  expect(buttonGroup.find(Button).at(1).text()).toBe('Running, Canceled or Completed');
  expect(buttonGroup.find(Button).at(2).text()).toBe('Not executed');
  expect(buttonGroup.find(Button).at(3).text()).toBe('Canceled');
});

it('should set the operator when clicking the operator buttons', () => {
  const node = shallow(<NodeFilter {...props} />);

  runAllEffects();

  node.find(Button).at(1).simulate('click');
  expect(node.find(NodeListPreview).props().operator).toBe('in');

  node.find(Button).at(2).simulate('click');
  expect(node.find(NodeListPreview).prop('operator')).toBe('not in');
});

it('should initially load xml', () => {
  shallow(<NodeFilter {...props} />);

  runAllEffects();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('definitionKey', 'all', null);
});

it('should load new xml after changing definition', () => {
  const definitions = [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
    {
      identifier: 'otherDefinition',
      key: 'otherDefinitionKey',
      versions: ['1'],
      tenantIds: ['marketing', 'sales'],
    },
  ];
  const node = shallow(<NodeFilter {...props} definitions={definitions} />);

  runAllEffects();

  node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);
  node.find(ClickBehavior).prop('onClick')({
    name: 'foo',
    id: 'bar',
  });

  runAllEffects();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('otherDefinitionKey', '1', 'marketing');
});
