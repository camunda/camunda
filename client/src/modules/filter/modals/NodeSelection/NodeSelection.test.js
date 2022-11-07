/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
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
  const node = shallow(<NodeSelection {...props} />);

  await runAllEffects();

  expect(node.find(BPMNDiagram).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', async () => {
  const node = shallow(<NodeSelection {...props} />);

  const flowNode = {
    name: 'bar',
    id: 'bar',
  };

  await runAllEffects();

  node.find('ClickBehavior').prop('onClick')(flowNode);

  expect(node.find('ClickBehavior').prop('selectedNodes')).toContain('bar');
});

it('should remove a selected node from the selectedNodes on toggle', async () => {
  const node = shallow(<NodeSelection {...props} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  await runAllEffects();

  node.find('ClickBehavior').prop('onClick')(flowNode);
  node.find('ClickBehavior').prop('onClick')(flowNode);

  expect(node.find('ClickBehavior').prop('selectedNodes')).not.toContain('bar');
});

it('should invoke addFilter when applying the filter', async () => {
  const spy = jest.fn();
  const node = shallow(<NodeSelection {...props} onClose={() => {}} addFilter={spy} />);

  await runAllEffects();

  node.find('ClickBehavior').prop('onClick')({id: 'a'});

  node.find(Modal.Actions).find(Button).at(1).simulate('click');

  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'not in', values: ['a']},
    type: 'executedFlowNodes',
    appliedTo: ['definition'],
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(
    <NodeSelection {...props} filterData={{appliedTo: '', data: {flowNodeIds: []}}} />
  );

  const buttons = node.find(Modal.Actions).find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // apply filter
});

it('should disable create filter button if all nodes are selected', async () => {
  const node = await shallow(<NodeSelection {...props} />);

  await runAllEffects();

  node.find('ClickBehavior').prop('onClick')({id: 'a'});
  node.find('ClickBehavior').prop('onClick')({id: 'b'});
  node.find('ClickBehavior').prop('onClick')({id: 'c'});

  expect(node.find(Modal.Actions).find(Button).at(1).prop('disabled')).toBeTruthy();
});

it('should deselect All nodes if deselectAll button is clicked', async () => {
  const node = shallow(<NodeSelection {...props} />);

  await runAllEffects();

  node.find(Modal.Content).find(Button).at(1).simulate('click');

  expect(node.find('ClickBehavior').prop('selectedNodes')).toEqual([]);
});

it('should initially load xml', async () => {
  shallow(<NodeSelection {...props} />);

  await runAllEffects();

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

  await runAllEffects();

  node.find(FilterSingleDefinitionSelection).prop('setApplyTo')(definitions[1]);

  await runAllEffects();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('otherDefinitionKey', '1', 'marketing');
});
