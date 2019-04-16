/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';

import {PANE_ID, EXPAND_STATE, DIRECTION} from 'modules/constants';

import Pane from './Pane';
import * as Styled from './styled';

const SplitPaneHeader = () => <div>Header Content</div>;
const SplitPaneBody = () => <div>Body Content</div>;

const mockDefaultProps = {
  handleExpand: jest.fn()
};

const mountNode = mockCustomProps => {
  return mount(
    <ThemeProvider>
      <Pane {...mockDefaultProps} {...mockCustomProps}>
        <SplitPaneHeader />
        <SplitPaneBody />
      </Pane>
    </ThemeProvider>
  );
};

describe('Pane', () => {
  describe('top pane', () => {
    it('should not render expand buttons', () => {
      // given
      const node = mountNode({
        expandState: EXPAND_STATE.COLLAPSED
      });

      // then
      const ExpandButtonNode = node.find(Styled.PaneExpandButton);
      expect(ExpandButtonNode).toHaveLength(0);
    });
  });

  describe('bottom pane', () => {
    it('should render ExpandButton with UP icon if pane is collapsed', () => {
      // given
      const node = mountNode({
        paneId: PANE_ID.BOTTOM,
        expandState: EXPAND_STATE.COLLAPSED
      });

      // then
      const ExpandButtonNode = node.find(Styled.PaneExpandButton);

      expect(ExpandButtonNode).toHaveLength(1);
      expect(ExpandButtonNode.prop('direction')).toBe(DIRECTION.UP);
    });

    it("'should render both ExpandButtons if pane is in default position", () => {
      // given
      const node = mountNode({
        paneId: PANE_ID.BOTTOM,
        expandState: EXPAND_STATE.DEFAULT
      });

      // then
      const ExpandButtonNodes = node.find(Styled.PaneExpandButton);
      expect(ExpandButtonNodes).toHaveLength(2);
      expect(ExpandButtonNodes.at(0).prop('direction')).toBe(DIRECTION.DOWN);
      expect(ExpandButtonNodes.at(1).prop('direction')).toBe(DIRECTION.UP);
    });

    it("should render ExpandButton with DOWN icon if pane is expanded'", () => {
      // given
      const node = mountNode({
        paneId: PANE_ID.BOTTOM,
        expandState: EXPAND_STATE.EXPANDED
      });

      // then
      const ExpandButtonNode = node.find(Styled.PaneExpandButton);
      expect(ExpandButtonNode).toHaveLength(1);
      expect(ExpandButtonNode.prop('direction')).toBe(DIRECTION.DOWN);
    });
  });

  describe('handleExpand', () => {
    const mockDefaultProps = {
      handleExpand: jest.fn(),
      resetExpanded: jest.fn(),
      paneId: PANE_ID.BOTTOM
    };

    beforeEach(() => {
      mockDefaultProps.handleExpand.mockClear();
      mockDefaultProps.resetExpanded.mockClear();
    });

    describe('handleTopExpand', () => {
      it('should call handleExpand with PANE_ID.TOP', () => {
        // given
        const node = mountNode(mockDefaultProps);
        const PaneNode = node.find(Pane);

        // when
        expect(PaneNode.instance().handleTopExpand());

        // then
        expect(mockDefaultProps.handleExpand).toHaveBeenCalledWith(PANE_ID.TOP);
      });
    });

    describe('handleBottomExpand', () => {
      it('should call handleExpand with PANE_ID.BOTTOM', () => {
        // given
        const node = mountNode(mockDefaultProps);
        const PaneNode = node.find(Pane);

        // when
        expect(PaneNode.instance().handleBottomExpand());

        // then
        expect(mockDefaultProps.handleExpand).toHaveBeenCalledWith(
          PANE_ID.BOTTOM
        );
      });
    });
  });
});
