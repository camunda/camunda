/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const HEIGHT = 4;
const SHIMMER_WIDTH = 70;

const Container = styled.div`
  position: relative;
  height: ${HEIGHT}px;
  width: 100%;
`;

const Background = styled.div`
  ${({theme}) => {
    const opacity = theme.opacity.progressBar.background;

    return css`
      background-color: ${theme.colors.selections};
      opacity: ${opacity};
      position: absolute;
      height: ${HEIGHT}px;
      width: 100%;
    `;
  }}
`;

type BarProps = {
  width: number;
};

const Bar = styled.div<BarProps>`
  ${({theme, width}) => {
    return css`
      @keyframes shimmer {
        0% {
          background-position: -${SHIMMER_WIDTH}px 0;
        }

        100% {
          background-position: calc(100% + ${SHIMMER_WIDTH}px) 0;
        }
      }

      background: linear-gradient(
        to right,
        ${theme.colors.selections},
        ${theme.colors.operationsProgressBar.shimmerColor},
        ${theme.colors.selections}
      );

      background-color: ${theme.colors.selections};
      background-size: ${SHIMMER_WIDTH}px 100%;
      background-position: -${SHIMMER_WIDTH}px 0;
      animation-name: shimmer;
      animation-duration: 2s;
      animation-iteration-count: infinite;
      background-repeat: no-repeat;
      position: absolute;
      height: ${HEIGHT}px;
      width: ${width}%;
      transition: width 1s ease;
    `;
  }}
`;

export {Container, Background, Bar};
