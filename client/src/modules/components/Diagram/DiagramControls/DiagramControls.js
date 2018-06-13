import React from 'react';

import {DiagramReset} from 'modules/components/Icon';

import * as Styled from './styled';

export default function DiagramControls({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut
}) {
  return (
    <Styled.DiagramControls>
      <Styled.ZoomReset onClick={handleZoomReset}>
        <DiagramReset />
      </Styled.ZoomReset>
      <Styled.ZoomIn onClick={handleZoomIn}>+</Styled.ZoomIn>
      <Styled.ZoomOut onClick={handleZoomOut}>-</Styled.ZoomOut>
    </Styled.DiagramControls>
  );
}
