/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import NodeListPreview from './NodeListPreview';

const props = {
  nodes: [{id: 'bar', name: 'foo'}],
  operator: 'in',
  type: 'executedFlowNodes',
};

it('should create preview of nodes', () => {
  const node = shallow(<NodeListPreview {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show the id of the flow node if the name is null', () => {
  const node = shallow(<NodeListPreview {...props} nodes={[{id: 'bar', name: undefined}]} />);

  expect(node.find('b')).toMatchSnapshot();
});

it('should create preview of selected nodes linked by or', () => {
  const flowNode1 = {
    name: 'foo',
    id: 'bar',
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar',
  };
  const node = shallow(<NodeListPreview {...props} nodes={[flowNode1, flowNode2]} />);

  expect(node).toMatchSnapshot();
});

it('should create preview of selected nodes linked by nor', () => {
  const flowNode1 = {
    name: 'foo',
    id: 'bar',
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar',
  };

  const changedProps = {
    nodes: [flowNode1, flowNode2],
    operator: 'not in',
  };

  const node = shallow(<NodeListPreview {...props} {...changedProps} />);

  expect(node).toMatchSnapshot();
});

it('should show executing node filter', () => {
  const node = shallow(
    <NodeListPreview {...props} operator={undefined} type="executingFlowNodes" />
  );

  expect(node.find('.parameterName')).toIncludeText('Running');
});
