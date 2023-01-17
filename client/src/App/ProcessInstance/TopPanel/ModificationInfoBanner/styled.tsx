/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {LinkButton} from 'modules/components/LinkButton';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.topPanel.modificationInfoBanner;
    return css`
      padding: 7px 0;
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      ${styles.bodyShort01};
      font-size: 11px;
      font-weight: bold;
      display: flex;
      justify-content: center;
      box-shadow: ${theme.shadows.topPanel.modificationInfoBanner};
    `;
  }}
`;

type TextProps = {
  containsButton?: boolean;
};

const Text = styled.div<TextProps>`
  ${({theme, containsButton}) => {
    const colors = theme.colors.topPanel.modificationInfoBanner;
    return css`
      padding: 0 16px;
      font-weight: 500;

      ${containsButton &&
      css`
        position: relative;

        &:after {
          content: ' ';
          position: absolute;
          right: 0;
          height: 13px;
          width: 0.7px;
          background-color: ${colors.separatorColor};
        }
      `}
    `;
  }}
`;

const Button = styled(LinkButton)`
  ${({theme}) => {
    const colors = theme.colors.topPanel.modificationInfoBanner.linkButton;

    return css`
      margin-left: 16px;
      text-decoration: none;
      font-size: 11px;
      font-weight: 500;

      color: ${colors.default};

      &:hover {
        color: ${colors.hover};
      }

      &:active {
        color: ${colors.active};
      }
    `;
  }}
`;

export {Container, Text, Button};
