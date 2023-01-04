/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

type BaseProps = {
  $hasFooter?: boolean;
  $variant: 'background' | 'layer';
};

const Header = styled.h5`
  ${({theme}) => {
    return css`
      ${theme.heading02};
      color: var(--cds-text-primary);
      padding: ${theme.spacing03} ${theme.spacing05};
      border-bottom: 1px solid var(--cds-border-subtle);
      display: flex;
      justify-content: space-between;
      align-items: center;
    `;
  }}
`;

const Base = styled.div<BaseProps>`
  ${({$hasFooter, $variant}) => {
    return css`
      background-color: ${$variant === 'background'
        ? css`var(--cds-background)`
        : css`var(--cds-layer)`};
      padding: 0;
      display: grid;
      grid-template-columns: 100%;
      grid-template-rows: ${$hasFooter
        ? css`
            ${rem(38)} auto ${rem(38)}
          `
        : css`
            ${rem(38)} auto
          `};
    `;
  }}
`;

const Body = styled.div`
  overflow-y: hidden;
  display: flex;
  flex-direction: column;
`;

const Footer = styled.div`
  ${({theme}) => {
    return css`
      height: ${rem(37)};
      color: var(--cds-text-helper);
      ${theme.legal01};
      text-align: right;
      padding-right: ${rem(20)};
      border-top: 1px solid var(--cds-border-subtle);
      display: flex;
      align-items: center;
      justify-content: flex-end;
    `;
  }}
`;

export {Base, Header, Body, Footer};
