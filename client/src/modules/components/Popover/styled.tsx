/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
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

type Props = {
  $side: Side;
};

const Arrow = styled.div<Props>`
  ${({theme, $side}) => {
    const colors = theme.colors.modules.popover;

    return css`
      position: absolute;
      width: ${ARROW_SIZE}px;
      height: ${ARROW_SIZE}px;
      background-color: ${colors.backgroundColor};
      transform: rotate(45deg);

      ${$side === 'top' &&
      css`
        border-bottom: 1px solid ${colors.arrowStyle.borderColor};
        border-right: 1px solid ${colors.backgroundColor};
      `}
      ${$side === 'bottom' &&
      css`
        border-left: 1px solid ${colors.arrowStyle.borderColor};
        border-top: 1px solid ${colors.backgroundColor};
      `}
      ${$side === 'right' &&
      css`
        border-bottom: 1px solid ${colors.arrowStyle.borderColor};
        border-left: 1px solid ${colors.backgroundColor};
      `}
      ${$side === 'left' &&
      css`
        border-right: 1px solid ${colors.arrowStyle.borderColor};
        border-top: 1px solid ${colors.backgroundColor};
      `}
    `;
  }}
`;

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.popover;
    const shadow = theme.shadows.modules.popover;

    return css`
      background-color: ${colors.backgroundColor};
      border: 1px solid ${colors.borderColor};
      box-shadow: ${shadow};
      color: ${theme.colors.text02};
      ${styles.bodyShort01};
      font-size: 12px;
      border-radius: 3px;
      cursor: auto;
    `;
  }}
`;

export {Container, Arrow, getArrowPosition};
