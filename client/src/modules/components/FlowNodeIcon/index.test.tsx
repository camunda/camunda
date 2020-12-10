/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';
import FlowNodeIcon from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {TYPE, MULTI_INSTANCE_TYPE} from 'modules/constants';

function getName(node: any) {
  return node.find('svg').text();
}

function mountIcon(types: any, flowNodeInstanceType: string) {
  return mount(
    <FlowNodeIcon types={types} flowNodeInstanceType={flowNodeInstanceType} />,
    {
      wrappingComponent: ThemeProvider,
    }
  );
}

describe('FlowNodeIcon', () => {
  it('should render default icon', () => {
    const node = mountIcon({}, '');
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render parallel multi instance body', () => {
    const node = mountIcon(
      {
        elementType: TYPE.TASK_SUBPROCESS,
        multiInstanceType: MULTI_INSTANCE_TYPE.PARALLEL,
      },
      TYPE.MULTI_INSTANCE_BODY
    );
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render sequential multi instance body', () => {
    const node = mountIcon(
      {
        elementType: TYPE.TASK_SUBPROCESS,
        multiInstanceType: MULTI_INSTANCE_TYPE.SEQUENTIAL,
      },
      TYPE.MULTI_INSTANCE_BODY
    );
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render intermediate timer event', () => {
    const node = mountIcon(
      {
        elementType: TYPE.EVENT_INTERMEDIATE_CATCH,
        eventType: TYPE.EVENT_TIMER,
      },
      TYPE.EVENT_INTERMEDIATE_CATCH
    );
    expect(getName(node)).toMatchSnapshot();
  });

  it('should render message boundary event', () => {
    const node = mountIcon(
      {
        elementType: TYPE.EVENT_BOUNDARY_NON_INTERRUPTING,
        eventType: TYPE.EVENT_MESSAGE,
      },
      TYPE.EVENT_BOUNDARY_NON_INTERRUPTING
    );
    expect(getName(node)).toMatchSnapshot();
  });
});
