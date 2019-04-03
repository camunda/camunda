/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import ProcessPart from './ProcessPart';

import {mount} from 'enzyme';

jest.mock('components', () => {
  const Modal = props => <div id="modal">{props.open && props.children}</div>;
  Modal.Header = props => <div id="modal_header">{props.children}</div>;
  Modal.Content = props => <div id="modal_content">{props.children}</div>;
  Modal.Actions = props => <div id="modal_actions">{props.children}</div>;

  return {
    Modal,
    Button: props => <button {...props} />,
    BPMNDiagram: () => <div>BPMNDiagram</div>,
    ClickBehavior: () => <div>ClickBehavior</div>,
    ActionItem: ({onClick, children}) => (
      <div>
        <button className="clearBtn" onClick={onClick}>
          X
        </button>
        {children}
      </div>
    ),
    Message: ({children}) => <div className="message">{children}</div>
  };
});

jest.mock('./PartHighlight', () => () => null);

it('should display a button if no process part is set', () => {
  const node = mount(<ProcessPart />);

  expect(
    node.findWhere(n => n.type() === 'button' && n.text() === 'Process Instance Part')
  ).toBePresent();
});

it('should not display the button is process part is set', () => {
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  expect(
    node.findWhere(n => n.type() === 'button' && n.text() === 'Process Instance Part')
  ).not.toBePresent();
});

it('should show a preview of the process part', () => {
  const node = mount(
    <ProcessPart
      processPart={{start: 'a', end: 'b'}}
      flowNodeNames={{a: 'Start Node', b: 'End Node'}}
    />
  );

  expect(node).toIncludeText('Only regard part between Start Node and End Node');
});

it('should remove the process part', () => {
  const spy = jest.fn();
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} update={spy} />);

  node.find('button').simulate('click');

  expect(spy).toHaveBeenCalledWith(null);
});

it('should open a modal when clicking the button', () => {
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  node.find('.ProcessPart__current').simulate('click');

  expect(node.state('modalOpen')).toBe(true);
});

it('should show the bpmn diagram', () => {
  const node = mount(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  node.find('.ProcessPart__current').simulate('click');

  expect(node.find('BPMNDiagram')).toBePresent();
});

it('should show the id of the selected node if it does not have a name', () => {
  const node = mount(<ProcessPart />);

  node.setState({
    modalOpen: true,
    start: {id: 'startId', name: 'Start Name'},
    end: {id: 'endId'}
  });

  expect(node.find('#modal_content')).toIncludeText('Start Name');
  expect(node.find('#modal_content')).toIncludeText('endId');
});

it('should deselect a node when it is clicked and already selected', () => {
  const node = mount(<ProcessPart />);

  const flowNode = {id: 'nodeId', name: 'Some node'};

  node.setState({
    modalOpen: true,
    start: flowNode
  });

  node.instance().selectNode(flowNode);

  expect(node.state('start')).toBe(null);
});

it('should display a warning if there is no path between start and end node', () => {
  const node = mount(<ProcessPart />);

  const flowNode = {id: 'nodeId', name: 'Some node'};

  node.setState({
    modalOpen: true,
    start: flowNode,
    end: {id: 'anId'},
    hasPath: false
  });

  expect(node.find('.message')).toIncludeText('Report results may be empty or misleading.');
});
