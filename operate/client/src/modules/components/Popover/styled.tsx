/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import type {Side} from '@floating-ui/react-dom';

const ARROW_SIZE = 18;

function getArrowPosition({
  side,
  x,
  y,
}: {
  side: Side;
  x: number;
  y: number;
}):
  | {bottom: number; left: number}
  | {top: number; right: number}
  | {top: number; left: number} {
  if (side === 'top') {
    return {
      left: x,
      bottom: -(ARROW_SIZE / 2),
    };
  }

  if (side === 'bottom') {
    return {
      left: x,
      top: -(ARROW_SIZE / 2),
    };
  }

  if (side === 'left') {
    return {
      top: y,
      right: -(ARROW_SIZE / 2),
    };
  }

  return {
    top: y,
    left: -(ARROW_SIZE / 2),
  };
}

const Arrow = styled.div`
  position: absolute;
  width: ${ARROW_SIZE}px;
  height: ${ARROW_SIZE}px;
  background-color: var(--cds-layer);
  transform: rotate(45deg);
`;

const Container = styled.div`
  background-color: var(--cds-layer);
  box-shadow: 0 2px 6px var(--cds-shadow);
  color: var(--cds-text-secondary);
  padding: var(--cds-spacing-05);
`;

export {Container, Arrow, getArrowPosition};
