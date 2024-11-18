/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps} from 'react';
import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';
import {Button} from '@carbon/react';

import {Modal, BPMNDiagram, ClickBehavior} from 'components';
import {loadProcessDefinitionXml} from 'services';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import NodeSelection from './NodeSelection';

jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

jest.mock('bpmn-js/lib/NavigatedViewer', () => {
  return class Viewer {
    elements: {id: string; name: string}[];
    elementRegistry: {filter: () => {map: () => {id: string; name: string}[]}};
    constructor() {
      this.elements = [
        {id: 'a', name: 'Element A'},
        {id: 'b', name: 'Element B'},
        {id: 'c', name: 'Element C'},
      ];

      this.elementRegistry = {
        filter: () => {
          return {
            map: () => this.elements,
          };
        },
      };
    }
    attachTo = jest.fn();
    importXML = jest.fn();
    get = () => {
      return this.elementRegistry;
    };
  };
});

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadProcessDefinitionXml: jest.fn().mockReturnValue('fooXml'),
}));

beforeEach(() => {
  (loadProcessDefinitionXml as jest.Mock).mockClear();
});

const props: ComponentProps<typeof NodeSelection> = {
  filterLevel: 'view',
  filterType: 'executedFlowNodes',
  close: jest.fn(),
  addFilter: jest.fn(),
  definitions: [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
  ],
};

it('should contain a modal', () => {
  const node = shallow(<NodeSelection {...props} />);

  expect(node.find(Modal)).toExist();
});

it('should display a diagram', async () => {
  const node = shallow(<NodeSelection {...props} />);

  runAllEffects();
  await flushPromises();

  expect(node.find(BPMNDiagram).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', async () => {
  const node = shallow(<NodeSelection {...props} />);

  const flowNode = {
    name: 'bar',
    id: 'bar',
  };

  runAllEffects();
  await flushPromises();

  node.find(ClickBehavior).prop('onClick')(flowNode);

  expect(node.find(ClickBehavior).prop('selectedNodes')).toContain('bar');
});

it('should remove a selected node from the selectedNodes on toggle', async () => {
  const node = shallow(<NodeSelection {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  runAllEffects();
  await flushPromises();

  node.find(ClickBehavior).prop('onClick')(flowNode);
  node.find(ClickBehavior).prop('onClick')(flowNode);

  expect(node.find(ClickBehavior).prop('selectedNodes')).not.toContain('bar');
});

it('should invoke addFilter when applying the filter', async () => {
  const spy = jest.fn();
  const node = shallow(<NodeSelection {...props} addFilter={spy} />);

  runAllEffects();
  await flushPromises();

  node.find(ClickBehavior).prop('onClick')({id: 'a'});

  node.find(Modal.Footer).find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'not in', values: ['a']},
    type: 'executedFlowNodes',
    appliedTo: ['definition'],
  });
});

it('should use the in operator when the more than half of the nodes are deselected', async () => {
  const spy = jest.fn();
  const node = shallow(<NodeSelection {...props} addFilter={spy} />);

  runAllEffects();
  await flushPromises();

  node.find(ClickBehavior).prop('onClick')({id: 'a'});
  node.find(ClickBehavior).prop('onClick')({id: 'b'});

  node.find(Modal.Footer).find('.confirm').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: ['c']},
    type: 'executedFlowNodes',
    appliedTo: ['definition'],
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(
    <NodeSelection
      {...props}
      filterData={{type: 'executedFlowNodes', appliedTo: [], data: {values: []}}}
    />
  );

  const buttons = node.find(Modal.Footer).find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // apply filter
});

it('should disable create filter button if all nodes are selected', async () => {
  const node = await shallow(<NodeSelection {...props} />);

  runAllEffects();
  await flushPromises();

  node.find(ClickBehavior).prop('onClick')({id: 'a'});
  node.find(ClickBehavior).prop('onClick')({id: 'b'});
  node.find(ClickBehavior).prop('onClick')({id: 'c'});

  expect(node.find(Modal.Footer).find('.confirm').prop('disabled')).toBeTruthy();
});

it('should deselect/select All nodes using the provided buttons', async () => {
  const node = shallow(<NodeSelection {...props} />);

  runAllEffects();
  await flushPromises();

  node.find('.diagramActions Button').at(1).simulate('click');
  expect(node.find(ClickBehavior).prop('selectedNodes')).toEqual([]);

  node.find('.diagramActions Button').at(0).simulate('click');
  expect(node.find(ClickBehavior).prop('selectedNodes')).toEqual(['a', 'b', 'c']);
});

it('should initially load xml', async () => {
  shallow(<NodeSelection {...props} />);

  runAllEffects();
  await flushPromises();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('definitionKey', 'all', null);
});

it('should load new xml after changing definition', async () => {
  const definitions = [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
    {
      identifier: 'otherDefinition',
      key: 'otherDefinitionKey',
      versions: ['1'],
      tenantIds: ['marketing', 'sales'],
    },
  ];
  const node = shallow(<NodeSelection {...props} definitions={definitions} />);

  runAllEffects();
  await flushPromises();

  node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);

  runAllEffects();
  await flushPromises();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('otherDefinitionKey', '1', 'marketing');
});

it('should populate selected values correctly', async () => {
  const filterData: ComponentProps<typeof NodeSelection>['filterData'] = {
    type: 'executedFlowNodes',
    appliedTo: props.definitions[0] ? [props.definitions[0].identifier] : [],
    data: {operator: 'in', values: ['a']},
  };
  const spy = jest.fn();
  const node = shallow(<NodeSelection {...props} filterData={filterData} addFilter={spy} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('ClickBehavior').prop('selectedNodes')).toEqual(['a']);

  node.find(Modal.Footer).find('.confirm').simulate('click');
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: ['a']},
    type: 'executedFlowNodes',
    appliedTo: ['definition'],
  });
});

it('should replace selected nodes when changing definition', async () => {
  const definitions = [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
    {
      identifier: 'otherDefinition',
      key: 'otherDefinitionKey',
      versions: ['1'],
      tenantIds: ['marketing', 'sales'],
    },
  ];
  const node = shallow(<NodeSelection {...props} definitions={definitions} />);

  runAllEffects();
  await flushPromises();

  node.find(ClickBehavior).prop('onClick')({id: 'a'});

  node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);

  runAllEffects();
  await flushPromises();

  expect(node.find(ClickBehavior).prop('selectedNodes')).toEqual(['a', 'b', 'c']);
});

it('should populate empty values correctly', async () => {
  const filterData: ComponentProps<typeof NodeSelection>['filterData'] = {
    type: 'executedFlowNodes',
    appliedTo: props.definitions[0] ? [props.definitions[0].identifier] : [],
    data: {operator: 'in', values: []},
  };
  const node = shallow(<NodeSelection {...props} filterData={filterData} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('ClickBehavior').prop('selectedNodes')).toEqual([]);
});

it('should populate select all flow node for new filter', async () => {
  const node = shallow(<NodeSelection {...props} filterData={undefined} />);

  runAllEffects();
  await flushPromises();

  expect(node.find('ClickBehavior').prop('selectedNodes')).toEqual(['a', 'b', 'c']);
});

it('should select all flow nodes if definition changed', async () => {
  const filterData: ComponentProps<typeof NodeSelection>['filterData'] = {
    type: 'executedFlowNodes',
    appliedTo: props.definitions[0] ? [props.definitions[0].identifier] : [],
    data: {operator: 'in', values: ['a']},
  };

  const definitions = [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
    {
      identifier: 'otherDefinition',
      key: 'otherDefinitionKey',
      versions: ['1'],
      tenantIds: ['marketing', 'sales'],
    },
  ];

  const node = shallow(
    <NodeSelection {...props} definitions={definitions} filterData={filterData} />
  );

  node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);

  runAllEffects();
  await flushPromises();

  expect(node.find('ClickBehavior').prop('selectedNodes')).toEqual(['a', 'b', 'c']);
});
