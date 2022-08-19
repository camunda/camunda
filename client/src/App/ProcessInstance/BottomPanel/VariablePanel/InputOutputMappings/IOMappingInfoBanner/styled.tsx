/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {styles} from '@carbon/elements';
import {ReactComponent as BaseInfoIcon} from 'modules/components/Icon/notification-icon-info-blue.svg';
import {ReactComponent as BaseCloseIcon} from 'modules/components/Icon/close.svg';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.variablesPanel.ioMappings.banner;

    return css`
      ${styles.bodyShort01};
      font-weight: 500;
      background-color: ${colors.backgroundColor};
      padding: 14px 20px 13px 25px;
      margin: 31px 20px 18px 20px;
      display: flex;
      align-items: flex-start;
      border-radius: 3px;
      box-shadow: ${theme.shadows.variablesPanel.ioMappings.banner};
    `;
  }}
`;

const Text = styled.span`
  ${({theme}) => {
    return css`
      padding-left: 15px;
      color: ${theme.colors.text01};
    `;
  }}
`;

const InfoIcon = styled(BaseInfoIcon)`
  width: 24px;
  height: 25px;
  flex-shrink: 0;
`;

const Button = styled.button`
  padding: 0;
  margin: 0;
  background: transparent;
  margin-left: 20px;
`;

const CloseIcon = styled(BaseCloseIcon)`
  ${({theme}) => {
    const colors = theme.colors.variablesPanel.ioMappings.banner;

    return css`
      width: 14px;
      height: 15px;
      flex-shrink: 0;
      color: ${colors.closeIconColor};
    `;
  }}
`;

export {Container, Text, InfoIcon, Button, CloseIcon};
