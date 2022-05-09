/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import ProcessPart from './ProcessPart';

import {shallow} from 'enzyme';
import {Button, BPMNDiagram} from 'components';

it('should display a button if no process part is set', () => {
  const node = shallow(<ProcessPart />);

  expect(node.find(Button).filterWhere((n) => n.text() === 'Process Instance Part')).toExist();
});

it('should not display the button if process part is set', () => {
  const node = shallow(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  expect(node.find(Button).filterWhere((n) => n.text() === 'Process Instance Part')).not.toExist();
});

it('should show a preview of the process part', () => {
  const node = shallow(
    <ProcessPart processPart={{start: 'a', end: 'b'}} flowNodeNames={{a: 'Start Node'}} />
  );

  expect(node.find('ActionItem')).toMatchSnapshot();
});

it('should remove the process part', () => {
  const spy = jest.fn();
  const node = shallow(<ProcessPart processPart={{start: 'a', end: 'b'}} update={spy} />);

  node.find('ActionItem').at(0).prop('onClick')({stopPropagation: jest.fn()});

  expect(spy).toHaveBeenCalledWith(null);
});

it('should open a modal when clicking the button', () => {
  const node = shallow(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  node.find('ActionItem').prop('onEdit')();

  expect(node.state('modalOpen')).toBe(true);
});

it('should show the bpmn diagram', () => {
  const node = shallow(<ProcessPart processPart={{start: 'a', end: 'b'}} />);

  node.find('.ProcessPart__current').simulate('click');

  expect(node.find(BPMNDiagram)).toExist();
});

it('should deselect a node when it is clicked and already selected', () => {
  const node = shallow(<ProcessPart />);

  const flowNode = {id: 'nodeId', name: 'Some node'};

  node.setState({
    modalOpen: true,
    start: flowNode,
  });

  node.instance().selectNode(flowNode);

  expect(node.state('start')).toBe(null);
});

it('should display a warning if there is no path between start and end node', () => {
  const node = shallow(<ProcessPart />);

  const flowNode = {id: 'nodeId', name: 'Some node'};

  node.setState({
    modalOpen: true,
    start: flowNode,
    end: {id: 'anId'},
    hasPath: false,
  });

  expect(node.find('MessageBox').dive()).toIncludeText(
    'Report results may be empty or misleading.'
  );
});
