/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import styled, {css} from 'styled-components';

interface BaseProps {
  hasFooter?: boolean;
}

const Header = styled.div`
  ${({theme}) => {
    return css`
      height: 31px;
      line-height: 32px;
      font-size: 15px;
      font-weight: bold;
      background-color: ${theme.colors.ui02};
      color: ${theme.colors.ui06};
      padding: 3px 0 3px 19px;
      border-bottom: 1px solid ${theme.colors.ui05};
      display: flex;
      justify-content: space-between;
    `;
  }}
`;

const Base = styled.div<BaseProps>`
  ${({hasFooter}) => {
    return css`
      display: grid;
      grid-template-columns: 100%;
      grid-template-rows: ${hasFooter ? '38px auto 38px' : '38px auto'};
    `;
  }}
`;

interface BodyProps {
  hasTransparentBackground?: boolean;
}

const Body = styled.div<BodyProps>`
  ${({hasTransparentBackground, theme}) => {
    return css`
      background-color: ${hasTransparentBackground
        ? 'transparent'
        : theme.colors.ui04};
      overflow-y: hidden;
      display: flex;
      flex-direction: column;
    `;
  }}
`;

const Footer = styled.div`
  ${({theme}) => {
    return css`
      height: 37px;
      background-color: ${theme.colors.ui02};
      border-top: 1px solid ${theme.colors.ui05};
      color: ${theme.colors.text.copyrightNotice};
      font-size: 12px;
      text-align: right;
      line-height: 38px;
      padding-right: 20px;
    `;
  }}
`;

export {Base, Header, Body, Footer};
