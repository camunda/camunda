/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import NodeListPreview from './NodeListPreview';
import {shallow} from 'enzyme';

it('should create preview of nodes', () => {
  const props = {
    nodes: [{id: 'bar', name: 'foo'}],
    operator: 'in'
  };

  const node = shallow(<NodeListPreview {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show the id of the flow node if the name is null', () => {
  const props = {
    nodes: [{id: 'bar', name: undefined}],
    operator: 'in'
  };

  const node = shallow(<NodeListPreview {...props} />);

  expect(node.find('.previewItemValue')).toIncludeText(props.nodes[0].id);
});

it('should create preview of selected nodes linked by or', () => {
  const flowNode1 = {
    name: 'foo',
    id: 'bar'
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar'
  };

  const props = {
    nodes: [flowNode1, flowNode2],
    operator: 'in'
  };

  const node = shallow(<NodeListPreview {...props} />);

  expect(node).toMatchSnapshot();
});

it('should create preview of selected nodes linked by nor', () => {
  const flowNode1 = {
    name: 'foo',
    id: 'bar'
  };

  const flowNode2 = {
    name: 'foo',
    id: 'bar'
  };

  const props = {
    nodes: [flowNode1, flowNode2],
    operator: 'not in'
  };

  const node = shallow(<NodeListPreview {...props} />);

  expect(node).toMatchSnapshot();
});

it('should show executing node filter if operator is undefined', () => {
  const props = {
    nodes: [{id: 'bar', name: undefined}],
    operator: undefined
  };

  const node = shallow(<NodeListPreview {...props} />);

  expect(node.find('.parameterName')).toIncludeText('Executing Flow Node');
});
