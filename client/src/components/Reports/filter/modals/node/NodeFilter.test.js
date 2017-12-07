import React from 'react';

import NodeFilter from './NodeFilter';
import {loadDiagramXML} from './service';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id='modal'>{props.children}</div>;
  Modal.Header = props => <div id='modal_header'>{props.children}</div>;
  Modal.Content = props => <div id='modal_content'>{props.children}</div>;
  Modal.Actions = props => <div id='modal_actions'>{props.children}</div>;

  return {
  Modal,
  Button: props => <button {...props}>{props.children}</button>,
  BPMNDiagram: props => <div>Diagram {props.children}</div>
}});

jest.mock('./service', () => {
  return {
    loadDiagramXML: jest.fn().mockReturnValue('someDiagramXML')
  }
});

jest.mock('./ClickBehavior', () => props => <span>ClickBehavior</span>);

it('should contain a modal', () => {
  const node = mount(<NodeFilter />);

  expect(node.find('#modal')).toBePresent();
});

it('should initially load the process diagram', () => {
  mount(<NodeFilter processDefinitionId='procDefId' />);

  expect(loadDiagramXML).toHaveBeenCalledWith('procDefId');
});

it('should display a diagram', async () => {
  const node = mount(<NodeFilter processDefinitionId='procDefId' />);

  await node.instance().loadDiagram();

  expect(node).toIncludeText('Diagram');
});

it('should add an unselected node to the selectedNodes on toggle', () => {
  const node = mount(<NodeFilter processDefinitionId='procDefId' />);

  node.instance().toggleNode('node1');

  expect(node.state().selectedNodes).toContain('node1');
});

it('should remove a selected node from the selectedNodes on toggle', () => {
  const node = mount(<NodeFilter processDefinitionId='procDefId' />);

  node.instance().toggleNode('node1');
  node.instance().toggleNode('node1');

  expect(node.state().selectedNodes).not.toContain('node1');
});

it('should create a new filter', () => {
  const spy = jest.fn();
  const node = mount(<NodeFilter processDefinitionId='procDefId' addFilter={spy} />);

  node.setState({
    selectedNodes: ['node1', 'node2']
  });

  node.find('button[type="primary"]').simulate('click');

  expect(spy).toHaveBeenCalledWith({
    type: 'executedFlowNodes',
    data: {
      operator: 'in',
      values: ['node1', 'node2']
    }
  });
});

it('should disable create filter button if no node was selected', () => {
  const node = mount(<NodeFilter processDefinitionId='procDefId'  />);
  node.setState({
    selectedNodes: []
  });
  
  const buttons = node.find("#modal_actions button");
  expect(buttons.at(0).prop("disabled")).toBeFalsy(); // abort
  expect(buttons.at(1).prop("disabled")).toBeTruthy(); // create filter
});

it('should create preview of selected node', () => {
  const node = mount(<NodeFilter processDefinitionId='procDefId' />);
  
    node.instance().toggleNode('node1');
  
    expect(node.find('#modal_content')).toIncludeText('node1');
});

it('should create preview of selected nodes linked by or', () => {
  const node = mount(<NodeFilter processDefinitionId='procDefId' />);
  
    node.instance().toggleNode('node1');
    node.instance().toggleNode('node2');
  
    const content = node.find('#modal_content');
    expect(content).toIncludeText('node1');
    expect(content).toIncludeText('or');
    expect(content).toIncludeText('node2');
});
