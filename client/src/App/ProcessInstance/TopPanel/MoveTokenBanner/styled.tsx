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
    const colors = theme.colors.topPanel.moveTokenBanner;

    return css`
      padding: 7px 0;
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      ${styles.bodyShort01};
      font-weight: 500;
      display: flex;
      justify-content: center;
      box-shadow: ${theme.shadows.topPanel.moveTokenBanner};
    `;
  }}
`;

const Text = styled.div`
  ${({theme}) => {
    const colors = theme.colors.topPanel.moveTokenBanner;
    return css`
      padding: 0 16px;
      font-weight: 500;
      position: relative;

      &:after {
        content: ' ';
        position: absolute;
        right: 0;
        height: 16px;
        width: 1px;
        background-color: ${colors.separatorColor};
      }
    `;
  }}
`;

const Button = styled(LinkButton)`
  margin-left: 16px;
  text-decoration: none;
  font-weight: 500;
`;

export {Container, Text, Button};
