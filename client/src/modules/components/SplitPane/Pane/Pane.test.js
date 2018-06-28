import React from 'react';
import {shallow} from 'enzyme';

import {ICON_DIRECTION} from 'modules/components/ExpandButton/constants';

import WithExpandPane from './Pane';
import {PANE_ID} from './constants';
import * as Styled from './styled';

const {WrappedComponent: Pane} = WithExpandPane;

describe('Pane', () => {
  const FooNode = () => <div>Foo</div>;
  const mockProps = {
    expand: jest.fn(),
    resetExpanded: jest.fn()
  };

  it('should render children', () => {
    // given
    const node = shallow(
      <Pane {...mockProps} paneId={PANE_ID.TOP}>
        <FooNode />
      </Pane>
    );

    // then
    expect(node.find(FooNode)).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });

  it("should render TopExpandButton with UP icon if pane is TOP and it's expanded", () => {
    // given
    const node = shallow(
      <Pane {...mockProps} paneId={PANE_ID.TOP} expandedId={PANE_ID.TOP}>
        <FooNode />
      </Pane>
    );

    // then
    const TopExpandButtonNode = node.find(Styled.TopExpandButton);
    expect(TopExpandButtonNode).toHaveLength(1);
    expect(TopExpandButtonNode.prop('iconDirection')).toBe(ICON_DIRECTION.UP);
    expect(node).toMatchSnapshot();
  });

  it("should render TopExpandButton with DOWN icon if pane is TOP and it's not expanded", () => {
    // given
    const node = shallow(
      <Pane {...mockProps} paneId={PANE_ID.TOP} expandedId={null}>
        <FooNode />
      </Pane>
    );

    // then
    const TopExpandButtonNode = node.find(Styled.TopExpandButton);
    expect(TopExpandButtonNode).toHaveLength(1);
    expect(TopExpandButtonNode.prop('iconDirection')).toBe(ICON_DIRECTION.DOWN);
    expect(node).toMatchSnapshot();
  });

  it("should render BottomExpandButton with DOWN icon if pane is BOTTOM and it's expanded", () => {
    // given
    const node = shallow(
      <Pane {...mockProps} paneId={PANE_ID.BOTTOM} expandedId={PANE_ID.BOTTOM}>
        <FooNode />
      </Pane>
    );

    // then
    const BottomExpandButtonNode = node.find(Styled.BottomExpandButton);
    expect(BottomExpandButtonNode).toHaveLength(1);
    expect(BottomExpandButtonNode.prop('iconDirection')).toBe(
      ICON_DIRECTION.DOWN
    );
    expect(node).toMatchSnapshot();
  });

  it("should render BottomExpandButton with UP icon if pane is BOTTOM and it's not expanded", () => {
    // given
    const node = shallow(
      <Pane {...mockProps} paneId={PANE_ID.BOTTOM} expandedId={PANE_ID.TOP}>
        <FooNode />
      </Pane>
    );

    // then
    const BottomExpandButtonNode = node.find(Styled.BottomExpandButton);
    expect(BottomExpandButtonNode).toHaveLength(1);
    expect(BottomExpandButtonNode.prop('iconDirection')).toBe(
      ICON_DIRECTION.UP
    );
    expect(node).toMatchSnapshot();
  });

  describe('handleExpand', () => {
    const mockProps = {
      expand: jest.fn(),
      resetExpanded: jest.fn(),
      paneId: PANE_ID.BOTTOM
    };

    beforeEach(() => {
      mockProps.expand.mockClear();
      mockProps.resetExpanded.mockClear();
    });

    it('should call expand with paneId when expandedId is null', () => {
      // given
      const node = shallow(<Pane {...mockProps} expandedId={null} />);

      // when
      expect(node.instance().handleExpand());

      // then
      expect(mockProps.expand).toHaveBeenCalledWith(mockProps.paneId);
    });

    it('should call resetExpanded when expandedId is not null', () => {
      // given
      const node = shallow(<Pane {...mockProps} expandedId={PANE_ID.TOP} />);

      // when
      expect(node.instance().handleExpand());

      // then
      expect(mockProps.resetExpanded).toHaveBeenCalled();
    });
  });
});
