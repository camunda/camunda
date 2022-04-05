/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Header = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.panel.panelHeader;

    return css`
      background-color: ${colors.backgroundColor};
      color: ${theme.colors.text01};
      border-bottom: solid 1px ${theme.colors.borderColor};
      font-size: 16px;
      font-weight: 600;
      padding: 8px 10px;
      padding-left: 20px;
      min-height: 37px;
      height: 37px;
      display: flex;
      align-items: center;
    `;
  }}
`;

export {Header};
