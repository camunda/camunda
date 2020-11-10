/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import NodeFilter from './NodeFilter';
import {Button} from 'components';
import {shallow} from 'enzyme';

it('should contain a modal', () => {
  const node = shallow(<NodeFilter />);

  expect(node.find('Modal')).toExist();
});

it('should display a diagram', () => {
  const node = shallow(<NodeFilter xml="fooXml" />);

  expect(node.find('.diagramContainer').childAt(0).props().xml).toBe('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = shallow(<NodeFilter />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).toContain(flowNode);
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = shallow(<NodeFilter />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  node.instance().toggleNode(flowNode);
  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).not.toContain(flowNode);
});

it('should create an executed node filter when operator is specified', () => {
  const spy = jest.fn();
  const node = shallow(<NodeFilter addFilter={spy} />);

  const flowNode1 = {
    name: 'foo',
    id: 'bar',
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar',
  };

  node.setState({
    selectedNodes: [flowNode1, flowNode2],
  });

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'executedFlowNodes',
    data: {
      operator: 'in',
      values: [flowNode1.id, flowNode2.id],
    },
  });
});

it('should set filter type depending on selected operation', () => {
  const spy = jest.fn();
  const node = shallow(<NodeFilter addFilter={spy} />);

  const flowNode1 = {
    name: 'foo',
    id: 'bar',
  };

  node.find(Button).at(0).simulate('click');

  node.setState({
    selectedNodes: [flowNode1],
  });

  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'executingFlowNodes',
    data: {
      operator: undefined,
      values: [flowNode1.id],
    },
  });

  node.find(Button).at(3).simulate('click');
  node.find('[primary]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'canceledFlowNodes',
    data: {
      operator: undefined,
      values: [flowNode1.id],
    },
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = shallow(<NodeFilter />);
  node.setState({
    selectedNodes: [],
  });

  expect(node.find('[primary]').prop('disabled')).toBeTruthy(); // create filter
});

it('should create preview list of selected node', () => {
  const node = shallow(<NodeFilter />);

  const flowNode = {
    name: 'foo',
    id: 'bar',
  };

  node.instance().toggleNode(flowNode);

  expect(node.find('NodeListPreview').props()).toEqual({
    nodes: [{id: 'bar', name: 'foo'}],
    operator: 'in',
    type: 'executedFlowNodes',
  });
});

it('should contain buttons to switch between executed and not executed mode', () => {
  const node = shallow(<NodeFilter />);

  expect(node.find('ButtonGroup')).toMatchSnapshot();
});

it('should set the operator when clicking the operator buttons', () => {
  const node = shallow(<NodeFilter />);

  node.find(Button).at(1).simulate('click');
  expect(node.state().operator).toBe('in');

  node.find(Button).at(2).simulate('click');
  expect(node.state().operator).toBe('not in');
});
