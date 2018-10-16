import React from 'react';
import {shallow} from 'enzyme';

import {DiagramReset, Plus, Minus} from 'modules/components/Icon';

import DiagramControls from './DiagramControls';
import * as Styled from './styled';

describe('DiagramControls', () => {
  it('should render diagram controls', () => {
    // given
    const handleZoomIn = jest.fn(),
      handleZoomOut = jest.fn(),
      handleZoomReset = jest.fn();
    const node = shallow(
      <DiagramControls
        handleZoomIn={handleZoomIn}
        handleZoomOut={handleZoomOut}
        handleZoomReset={handleZoomReset}
      />
    );

    // then
    expect(node.find(Styled.DiagramControls)).toHaveLength(1);
    expect(node.find(Styled.DiagramControls).props().isShifted).toBe(false);

    const ZoomResetNode = node.find(Styled.ZoomReset);
    expect(ZoomResetNode).toHaveLength(1);
    expect(ZoomResetNode.prop('onClick')).toBe(handleZoomReset);
    const DiagramResetNode = ZoomResetNode.find(DiagramReset);
    expect(DiagramResetNode).toHaveLength(1);

    const ZoomIn = node.find(Styled.ZoomIn);
    expect(ZoomIn).toHaveLength(1);
    expect(ZoomIn.prop('onClick')).toBe(handleZoomIn);
    const PlusNode = ZoomIn.find(Plus);
    expect(PlusNode).toHaveLength(1);

    const ZoomOut = node.find(Styled.ZoomOut);
    expect(ZoomOut).toHaveLength(1);
    expect(ZoomOut.prop('onClick')).toBe(handleZoomOut);
    const MinusNode = ZoomOut.find(Minus);
    expect(MinusNode).toHaveLength(1);
    expect(node).toMatchSnapshot();
  });
});
