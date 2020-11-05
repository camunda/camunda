/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const HEIGHT = 4;

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
      background-color: ${theme.colors.selections};
      position: absolute;
      height: ${HEIGHT}px;
      width: ${width}%;
    `;
  }}
`;

export {Container, Background, Bar};
