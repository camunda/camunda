/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import NodeFilter from './NodeFilter';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    ButtonGroup: props => <div {...props} />,
    Button: props => <button {...props}>{props.children}</button>,
    BPMNDiagram: props => (
      <div id="diagram">
        Diagram {props.children} {props.xml}
      </div>
    ),
    ClickBehavior: props => <span>ClickBehavior</span>
  };
});

it('should contain a modal', () => {
  const node = mount(<NodeFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should display a diagram', () => {
  const node = mount(<NodeFilter xml="fooXml" />);

  expect(node.find('#diagram')).toIncludeText('fooXml');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = mount(<NodeFilter />);

  const flowNode = {
    name: 'foo',
    id: 'bar'
  };

  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).toContain(flowNode);
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = mount(<NodeFilter />);

  const flowNode = {
    name: 'foo',
    id: 'bar'
  };

  node.instance().toggleNode(flowNode);
  node.instance().toggleNode(flowNode);

  expect(node.state().selectedNodes).not.toContain(flowNode);
});

it('should create a new filter', () => {
  const spy = jest.fn();
  const node = mount(<NodeFilter addFilter={spy} />);

  const flowNode1 = {
    name: 'foo',
    id: 'bar'
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar'
  };

  node.setState({
    selectedNodes: [flowNode1, flowNode2]
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'executedFlowNodes',
    data: {
      operator: 'in',
      values: [flowNode1.id, flowNode2.id]
    }
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = mount(<NodeFilter />);
  node.setState({
    selectedNodes: []
  });

  const buttons = node.find('#modal_actions button');
  expect(buttons.at(0).prop('disabled')).toBeFalsy(); // abort
  expect(buttons.at(1).prop('disabled')).toBeTruthy(); // create filter
});

it('should create preview of selected node', () => {
  const node = mount(<NodeFilter />);

  const flowNode = {
    name: 'foo',
    id: 'bar'
  };

  node.instance().toggleNode(flowNode);

  expect(node.find('#modal_content')).toIncludeText(flowNode.name);
});

it('should show the id of the flow node if the name is null', () => {
  const node = mount(<NodeFilter />);

  const flowNode = {
    name: null,
    id: 'bar'
  };

  node.instance().toggleNode(flowNode);

  expect(node.find('#modal_content')).toIncludeText(flowNode.id);
});

it('should create preview of selected nodes linked by or', () => {
  const node = mount(<NodeFilter />);

  const flowNode1 = {
    name: 'foo',
    id: 'bar'
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar'
  };

  node.instance().toggleNode(flowNode1);
  node.instance().toggleNode(flowNode2);

  const content = node.find('#modal_content');
  expect(content).toIncludeText(flowNode1.name);
  expect(content).toIncludeText('or');
  expect(content).toIncludeText(flowNode2.name);
});

it('should contain buttons to switch between executed and not executed mode', () => {
  const node = mount(<NodeFilter />);

  expect(node.find('button').at(0)).toIncludeText('was executed');
  expect(node.find('button').at(1)).toIncludeText('was not executed');
});

it('should set the operator when clicking the operator buttons', () => {
  const node = mount(<NodeFilter />);

  node
    .find('button')
    .at(0)
    .simulate('click');
  expect(node.state().operator).toBe('in');

  node
    .find('button')
    .at(1)
    .simulate('click');
  expect(node.state().operator).toBe('not in');
});
