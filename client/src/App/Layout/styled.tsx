/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

type GridProps = {
  numberOfRows?: 2 | 3;
};

const Grid = styled.div<GridProps>`
  ${({numberOfRows}) => {
    return css`
      width: 100%;
      height: 100%;
      display: grid;
      grid-template-columns: 100%;
      grid-template-rows: ${numberOfRows === 2
        ? css`57px calc(100vh - 57px)`
        : css`57px calc(100vh - 95px) 38px`};
    `;
  }}
`;

type Props = {
  variant?: 'dashboard' | 'default';
};

const Footer = styled.div<Props>`
  ${({theme, variant = 'default'}) => {
    const colors = theme.colors.layout;

    return css`
      padding: 12px 20px 11px;
      text-align: right;
      display: flex;
      align-items: flex-end;

      ${variant === 'dashboard'
        ? css`
            background-color: ${colors.dashboard.backgroundColor};
          `
        : ''}
      ${variant === 'default'
        ? css`
            border-top: solid 1px ${colors.default.borderColor};
            background-color: ${colors.default.backgroundColor};
          `
        : ''}
    `;
  }}
`;

export {Grid, Footer};
