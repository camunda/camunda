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
  it('should render children with handleExpand() and paneId props', () => {
    // given
    const FirstChild = () => <div>First Child</div>;
    const SecondChild = () => <div>Second Child</div>;
    const node = shallow(
      <SplitPane>
        <FirstChild />
        <SecondChild />
      </SplitPane>
    )
      .first()
      .shallow();

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
    it('should render children with proper expandState when no pane is extended', () => {
      // given
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;
      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      )
        .first()
        .shallow();

      // then
      const FirstChildNode = node.find(FirstChild);
      const SecondChildNode = node.find(SecondChild);
      expect(FirstChildNode).toHaveLength(1);
      expect(FirstChildNode.prop('expandState')).toBe(EXPAND_STATE.DEFAULT);
      expect(SecondChildNode).toHaveLength(1);
      expect(SecondChildNode.prop('expandState')).toBe(EXPAND_STATE.DEFAULT);
      expect(node).toMatchSnapshot();
    });

    it('should expand and collapse panels correctly', () => {
      const FirstChild = () => <div>First Child</div>;
      const SecondChild = () => <div>Second Child</div>;

      const node = shallow(
        <SplitPane>
          <FirstChild />
          <SecondChild />
        </SplitPane>
      )
        .first()
        .shallow();

      // Top panel is expanded on handle expand click
      node.find(FirstChild).prop('handleExpand')('TOP');
      node.update();
      expect(node.find(FirstChild).prop('expandState')).toBe(
        EXPAND_STATE.EXPANDED
      );
      expect(node.find(SecondChild).prop('expandState')).toBe(
        EXPAND_STATE.COLLAPSED
      );

      // Click top panel expand again, which makes it return to default state
      node.find(FirstChild).prop('handleExpand')('TOP');
      node.update();
      expect(node.find(FirstChild).prop('expandState')).toBe(
        EXPAND_STATE.DEFAULT
      );
      expect(node.find(SecondChild).prop('expandState')).toBe(
        EXPAND_STATE.DEFAULT
      );

      // Click bottom panel expand, which makes bottom panel expand
      node.find(SecondChild).prop('handleExpand')('BOTTOM');
      node.update();
      expect(node.find(FirstChild).prop('expandState')).toBe(
        EXPAND_STATE.COLLAPSED
      );
      expect(node.find(SecondChild).prop('expandState')).toBe(
        EXPAND_STATE.EXPANDED
      );

      // Click again and return to default state
      node.find(FirstChild).prop('handleExpand')('BOTTOM');
      node.update();
      expect(node.find(FirstChild).prop('expandState')).toBe(
        EXPAND_STATE.DEFAULT
      );
      expect(node.find(SecondChild).prop('expandState')).toBe(
        EXPAND_STATE.DEFAULT
      );
    });
  });
});
