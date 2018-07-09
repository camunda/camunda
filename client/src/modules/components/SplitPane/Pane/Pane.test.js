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

  it("should render TopExpandButton with UP icon if pane is TOP and it's expanded", () => {
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
    const TopExpandButtonNode = node.find(Styled.TopExpandButton);
    expect(TopExpandButtonNode).toHaveLength(1);
    expect(TopExpandButtonNode.prop('direction')).toBe(DIRECTION.UP);
    expect(node).toMatchSnapshot();
  });

  it("should render TopExpandButton with DOWN icon if pane is TOP and it's not expanded", () => {
    // given
    const node = shallow(
      <Pane
        {...mockProps}
        paneId={PANE_ID.TOP}
        expandState={EXPAND_STATE.DEFAULT}
      >
        <Foo />
      </Pane>
    );

    // then
    const TopExpandButtonNode = node.find(Styled.TopExpandButton);
    expect(TopExpandButtonNode).toHaveLength(1);
    expect(TopExpandButtonNode.prop('direction')).toBe(DIRECTION.DOWN);
    expect(node).toMatchSnapshot();
  });

  it("should render BottomExpandButton with DOWN icon if pane is BOTTOM and it's expanded", () => {
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
    const BottomExpandButtonNode = node.find(Styled.BottomExpandButton);
    expect(BottomExpandButtonNode).toHaveLength(1);
    expect(BottomExpandButtonNode.prop('direction')).toBe(DIRECTION.DOWN);
    expect(node).toMatchSnapshot();
  });

  it("should render BottomExpandButton with UP icon if pane is BOTTOM and it's not expanded", () => {
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
    const BottomExpandButtonNode = node.find(Styled.BottomExpandButton);
    expect(BottomExpandButtonNode).toHaveLength(1);
    expect(BottomExpandButtonNode.prop('direction')).toBe(DIRECTION.UP);
    expect(node).toMatchSnapshot();
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

    it('should call handleExpand with paneId', () => {
      // given
      const node = shallow(<Pane {...mockProps} />);

      // when
      expect(node.instance().handleExpand());

      // then
      expect(mockProps.handleExpand).toHaveBeenCalledWith(mockProps.paneId);
    });
  });
});
