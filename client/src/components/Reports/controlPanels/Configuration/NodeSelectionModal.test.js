/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Modal, BPMNDiagram, Button} from 'components';

import NodeSelectionModal from './NodeSelectionModal';

const report = {
  result: {
    data: [
      {key: 'foo', value: 123},
      {key: 'bar', value: 5},
    ],
  },
  data: {
    distributedBy: {},
    configuration: {
      color: 'testColor',
      xml: 'fooXml',
      hiddenNodes: {active: false, keys: []},
    },
    visualization: 'line',
    groupBy: {
      type: '',
      value: '',
    },
    view: {},
  },
  targetValue: false,
  combined: false,
};

it('should contain a modal', () => {
  const node = shallow(<NodeSelectionModal report={report} />);

  expect(node.find(Modal)).toExist();
});

it('should display a diagram', () => {
  const node = shallow(<NodeSelectionModal report={report} />);

  expect(node.find(BPMNDiagram).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = shallow(<NodeSelectionModal report={report} />);

  const flowNode = {
    name: 'bar',
    id: 'bar',
  };

  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).toEqual(['foo']);
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = shallow(<NodeSelectionModal report={report} />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  node.instance().toggleNode(flowNode);
  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).not.toContain(flowNode);
});

it('should invoke updateConfiguration when applying the filter', () => {
  const spy = jest.fn();
  const node = shallow(<NodeSelectionModal onClose={() => {}} report={report} onChange={spy} />);

  node.setState({
    selectedNodes: ['foo'],
  });

  node.find(Modal.Actions).find(Button).at(1).simulate('click');

  expect(spy).toHaveBeenCalledWith({
    hiddenNodes: {keys: {$set: [report.result.data[1].key]}},
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(<NodeSelectionModal report={report} />);
  node.setState({
    selectedNodes: [],
  });

  const buttons = node.find(Modal.Actions).find(Button);
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // apply filter
});

it('should deselect All nodes if deselectAll button is clicked', () => {
  const node = shallow(<NodeSelectionModal report={report} />);

  node.find(Modal.Content).find(Button).at(1).simulate('click');

  expect(node.state().selectedNodes).toEqual([]);
});
