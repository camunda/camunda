/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {
  FlowNodeTimeStampProvider,
  withFlowNodeTimeStampContext,
} from './FlowNodeTimeStampContext';

const Foo = withFlowNodeTimeStampContext(function Foo() {
  return <div>foo</div>;
});

function mountNode() {
  return mount(
    <FlowNodeTimeStampProvider>
      <Foo />
    </FlowNodeTimeStampProvider>
  );
}

describe('FlowNodeTimeStampContext', () => {
  it('should add properties to the wrapped component', () => {
    // given
    const node = mountNode();
    expect(node.find(Foo).find('[showTimeStamp=false]')).toExist();
  });

  describe('handleTimeStampToggle', () => {
    it('should toggle the state', () => {
      //given
      const node = mountNode();
      expect(node.state('showTimeStamp')).toBe(false);

      //when
      node.instance().handleTimeStampToggle();
      node.update();

      //then
      expect(node.state('showTimeStamp')).toBe(true);
      expect(node.find(Foo).find('[showTimeStamp=true]')).toExist();
    });
  });
});
