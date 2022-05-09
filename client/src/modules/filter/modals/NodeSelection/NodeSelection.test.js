/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Modal, BPMNDiagram, Button} from 'components';
import {loadProcessDefinitionXml} from 'services';

import FilterSingleDefinitionSelection from '../FilterSingleDefinitionSelection';

import {NodeSelection} from './NodeSelection';

jest.mock('bpmn-js/lib/NavigatedViewer', () => {
  return class Viewer {
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
  loadProcessDefinitionXml.mockClear();
});

const props = {
  close: jest.fn(),
  addFilter: jest.fn(),
  data: [],
  mightFail: (data, fn) => fn(data),
  definitions: [
    {identifier: 'definition', key: 'definitionKey', versions: ['all'], tenantIds: [null]},
  ],
};

it('should contain a modal', () => {
  const node = shallow(<NodeSelection {...props} />);

  expect(node.find(Modal)).toExist();
});

it('should display a diagram', async () => {
  const node = await shallow(<NodeSelection {...props} />);

  expect(node.find(BPMNDiagram).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = shallow(<NodeSelection {...props} />);

  const flowNode = {
    name: 'bar',
    id: 'bar',
  };

  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).toEqual(['bar']);
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = shallow(<NodeSelection {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  node.instance().toggleNode(flowNode);
  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).not.toContain(flowNode);
});

it('should invoke addFilter when applying the filter', async () => {
  const spy = jest.fn();
  const node = await shallow(<NodeSelection {...props} onClose={() => {}} addFilter={spy} />);

  node.setState({
    selectedNodes: ['a'],
  });

  node.find(Modal.Actions).find(Button).at(1).simulate('click');

  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'not in', values: ['b', 'c']},
    type: 'executedFlowNodes',
    appliedTo: ['definition'],
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(<NodeSelection {...props} />);
  node.setState({
    selectedNodes: [],
  });

  const buttons = node.find(Modal.Actions).find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // apply filter
});

it('should disable create filter button if all nodes are selected', async () => {
  const node = await shallow(<NodeSelection {...props} />);
  node.setState({
    selectedNodes: ['a', 'b', 'c'],
  });

  expect(node.find(Modal.Actions).find(Button).at(1).prop('disabled')).toBeTruthy();
});

it('should deselect All nodes if deselectAll button is clicked', () => {
  const node = shallow(<NodeSelection {...props} />);

  node.find(Modal.Content).find(Button).at(1).simulate('click');

  expect(node.state().selectedNodes).toEqual([]);
});

it('should initially load xml', async () => {
  await shallow(<NodeSelection {...props} />);

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
  const node = await shallow(<NodeSelection {...props} definitions={definitions} />);

  await node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('otherDefinitionKey', '1', 'marketing');
});
