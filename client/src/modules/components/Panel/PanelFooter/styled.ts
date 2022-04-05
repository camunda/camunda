/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Footer = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.panel.panelFooter;

    return css`
      min-height: 38px;
      max-height: 38px;
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      padding-right: 20px;
      border-top: solid 1px ${theme.colors.borderColor};
      background-color: ${colors.backgroundColor};
      color: ${theme.colors.text02};
    `;
  }}
`;

export {Footer};
