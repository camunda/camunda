/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ReactComponent as DiagramReset} from 'modules/components/Icon/diagram-reset.svg';
import {ReactComponent as Plus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as Minus} from 'modules/components/Icon/minus.svg';

import * as Styled from './styled';

type Props = {
  handleZoomReset: (...args: any[]) => any;
  handleZoomIn: (...args: any[]) => any;
  handleZoomOut: (...args: any[]) => any;
};

export default function DiagramControls({
  handleZoomReset,
  handleZoomIn,
  handleZoomOut,
}: Props) {
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
