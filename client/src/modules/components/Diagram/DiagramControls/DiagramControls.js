import React from 'react';

import {DiagramReset, Plus, Minus} from 'modules/components/Icon';

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
      <Styled.ZoomIn onClick={handleZoomIn}>
        <Plus />
      </Styled.ZoomIn>
      <Styled.ZoomOut onClick={handleZoomOut}>
        <Minus />
      </Styled.ZoomOut>
    </Styled.DiagramControls>
  );
}
