/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FOOTER_HEIGHT, PAGE_TOP_PADDING} from 'modules/constants';
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
        ? css`100%`
        : css`calc(100% - ${FOOTER_HEIGHT}px) ${FOOTER_HEIGHT}px`};
    `;
  }}
`;

const PageContent = styled.div`
  padding-top: ${PAGE_TOP_PADDING}px;
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
            border-top: solid 1px ${theme.colors.borderColor};
            background-color: ${colors.default.backgroundColor};
          `
        : ''}
    `;
  }}
`;

export {Grid, PageContent, Footer};
