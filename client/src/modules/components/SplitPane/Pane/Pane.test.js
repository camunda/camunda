import React from 'react';
import {shallow} from 'enzyme';

import {PANE_ID, EXPAND_STATE, DIRECTION} from 'modules/constants';

import Pane from './Pane';
import * as Styled from './styled';

describe('Pane', () => {
  const Foo = () => <div>Foo</div>;
  const mockProps = {
    handleExpand: jest.fn()
  };

  it('should render children with expandState', () => {
    // given
    const node = shallow(
      <Pane
        {...mockProps}
        paneId={PANE_ID.TOP}
        expandState={EXPAND_STATE.EXPANDED}
      >
        <Foo />
      </Pane>
    );

    // then
    const FooNode = node.find(Foo);
    expect(FooNode).toHaveLength(1);
    expect(FooNode.prop('expandState')).toBe(EXPAND_STATE.EXPANDED);
    expect(node).toMatchSnapshot();
  });

  describe('top pane', () => {
    it('should not render expand buttons', () => {
      // given
      const node = shallow(
        <Pane
          {...mockProps}
          paneId={PANE_ID.TOP}
          expandState={EXPAND_STATE.COLLAPSED}
        >
          <Foo />
        </Pane>
      );

      // then
      const ExpandButtonNode = node.find(Styled.PaneExpandButton);
      expect(ExpandButtonNode).toHaveLength(0);
      expect(node).toMatchSnapshot();
    });
  });

  describe('bottom pane', () => {
    it('should render ExpandButton with UP icon if pane is collapsed', () => {
      // given
      const node = shallow(
        <Pane
          {...mockProps}
          paneId={PANE_ID.BOTTOM}
          expandState={EXPAND_STATE.COLLAPSED}
        >
          <Foo />
        </Pane>
      );

      // then
      const ExpandButtonNode = node.find(Styled.PaneExpandButton);
      expect(ExpandButtonNode).toHaveLength(1);
      expect(ExpandButtonNode.prop('direction')).toBe(DIRECTION.UP);
      expect(node).toMatchSnapshot();
    });

    it("'should render both ExpandButtons by default", () => {
      // given
      const node = shallow(
        <Pane
          {...mockProps}
          paneId={PANE_ID.BOTTOM}
          expandState={EXPAND_STATE.DEFAULT}
        >
          <Foo />
        </Pane>
      );

      // then
      const ExpandButtonNodes = node.find(Styled.PaneExpandButton);
      expect(ExpandButtonNodes).toHaveLength(2);
      expect(ExpandButtonNodes.at(0).prop('direction')).toBe(DIRECTION.DOWN);
      expect(ExpandButtonNodes.at(1).prop('direction')).toBe(DIRECTION.UP);
      expect(node).toMatchSnapshot();
    });

    it("should render ExpandButton with DOWN icon if pane is expanded'", () => {
      // given
      const node = shallow(
        <Pane
          {...mockProps}
          paneId={PANE_ID.BOTTOM}
          expandState={EXPAND_STATE.EXPANDED}
        >
          <Foo />
        </Pane>
      );

      // then
      const ExpandButtonNode = node.find(Styled.PaneExpandButton);
      expect(ExpandButtonNode).toHaveLength(1);
      expect(ExpandButtonNode.prop('direction')).toBe(DIRECTION.DOWN);
      expect(node).toMatchSnapshot();
    });
  });

  describe('handleExpand', () => {
    const mockProps = {
      handleExpand: jest.fn(),
      resetExpanded: jest.fn(),
      paneId: PANE_ID.BOTTOM
    };

    beforeEach(() => {
      mockProps.handleExpand.mockClear();
      mockProps.resetExpanded.mockClear();
    });

    describe('handleTopExpand', () => {
      it('should call handleExpand with PANE_ID.TOP', () => {
        // given
        const node = shallow(<Pane {...mockProps} />);

        // when
        expect(node.instance().handleTopExpand());

        // then
        expect(mockProps.handleExpand).toHaveBeenCalledWith(PANE_ID.TOP);
      });
    });

    describe('handleBottomExpand', () => {
      it('should call handleExpand with PANE_ID.BOTTOM', () => {
        // given
        const node = shallow(<Pane {...mockProps} />);

        // when
        expect(node.instance().handleBottomExpand());

        // then
        expect(mockProps.handleExpand).toHaveBeenCalledWith(PANE_ID.BOTTOM);
      });
    });
  });
});
