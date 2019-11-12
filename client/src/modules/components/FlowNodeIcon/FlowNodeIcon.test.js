/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import FlowNodeIcon from './FlowNodeIcon';
import React from 'react';

import {TYPE, MULTI_INSTANCE_TYPE} from 'modules/constants';
import {mount} from 'enzyme';

function getName(node) {
  return node.find('svg').text();
}

function mountIcon(types) {
  return mount(<FlowNodeIcon types={types} />);
}

describe('FlowNodeIcon', () => {
  it('should render default icon', () => {
    const node = mountIcon({});
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render parallel multi instance body', () => {
    const node = mountIcon({
      elementType: TYPE.MULTI_INSTANCE_BODY,
      multiInstanceType: MULTI_INSTANCE_TYPE.PARALLEL
    });
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render sequential multi instance body', () => {
    const node = mountIcon({
      elementType: TYPE.MULTI_INSTANCE_BODY,
      multiInstanceType: MULTI_INSTANCE_TYPE.SEQUENTIAL
    });
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render intermediate timer event', () => {
    const node = mountIcon({
      elementType: TYPE.EVENT_INTERMEDIATE_CATCH,
      eventType: TYPE.EVENT_TIMER
    });
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render message boundary event', () => {
    const node = mountIcon({
      elementType: TYPE.EVENT_BOUNDARY_NON_INTERRUPTING,
      eventType: TYPE.EVENT_MESSAGE
    });
    expect(getName(node)).toMatchSnapshot();
  });
});
