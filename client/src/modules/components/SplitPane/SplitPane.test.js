/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {PANE_ID, EXPAND_STATE} from 'modules/constants';

import SplitPane from './SplitPane';

describe('SplitPane', () => {
  it('should have by default expandedPaneId null', () => {
    expect(new SplitPane().state.expandedPaneId).toBe(null);
  });

  it('should render children with handleExpand() and paneId props', () => {
    // given
    const FirstChild = () => <div>First Child</div>;
    const SecondChild = () => <div>Second Child</div>;
    const node = shallow(
      <SplitPane>
        <FirstChild />
        <SecondChild />
      </SplitPane>
    );

    // then
    const FirstChildNode = node.find(FirstChild);
    const SecondChildNode = node.find(SecondChild);
    expect(FirstChildNode).toHaveLength(1);
    expect(FirstChildNode.prop('paneId')).toBe(PANE_ID.TOP);
    expect(FirstChildNode.prop('handleExpand')).toBe(
      node.instance().handleExpand
    );
    expect(SecondChildNode).toHaveLength(1);
    expect(SecondChildNode.prop('paneId')).toBe(PANE_ID.BOTTOM);
    expect(SecondChildNode.prop('handleExpand')).toBe(
      node.instance().handleExpand
    );
  });

  describe('expandState', () => {
    it('should render children with proper expandState when TOP pane is extended', () => {
      // given
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;
      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      );
      node.setState({expandedPaneId: PANE_ID.TOP});
      node.update();

      // then
      const FirstChildNode = node.find(FirstChild);
      const SecondChildNode = node.find(SecondChild);
      expect(FirstChildNode).toHaveLength(1);
      expect(FirstChildNode.prop('expandState')).toBe(EXPAND_STATE.EXPANDED);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('expandState')).toBe(EXPAND_STATE.COLLAPSED);
      expect(node).toMatchSnapshot();
    });

    it('should render children with proper expandState when BOTTOM pane is extended', () => {
      // given
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;
      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      );
      node.setState({expandedPaneId: PANE_ID.BOTTOM});
      node.update();

      // then
      const FirstChildNode = node.find(FirstChild);
      const SecondChildNode = node.find(SecondChild);
      expect(FirstChildNode).toHaveLength(1);
      expect(FirstChildNode.prop('expandState')).toBe(EXPAND_STATE.COLLAPSED);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('expandState')).toBe(EXPAND_STATE.EXPANDED);
      expect(node).toMatchSnapshot();
    });

    it('should render children with proper expandState when no pane is extended', () => {
      // given
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;
      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      );
      node.setState({expandedPaneId: null});
      node.update();

      // then
      const FirstChildNode = node.find(FirstChild);
      const SecondChildNode = node.find(SecondChild);
      expect(FirstChildNode).toHaveLength(1);
      expect(FirstChildNode.prop('expandState')).toBe(EXPAND_STATE.DEFAULT);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('expandState')).toBe(EXPAND_STATE.DEFAULT);
      expect(node).toMatchSnapshot();
    });
  });
});
