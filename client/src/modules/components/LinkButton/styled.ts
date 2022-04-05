/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

type LinkButtonProps = {
  size?: 'small';
};

const LinkButton = styled.button<LinkButtonProps>`
  ${({theme, size}) => {
    return css`
      padding: 0;
      margin: 0;
      background: transparent;
      border: 0;
      font-size: ${size === 'small' ? 12 : 14}px;
      text-decoration: underline;
      color: ${theme.colors.linkDefault};

      &:hover {
        color: ${theme.colors.linkHover};
      }

      &:active {
        color: ${theme.colors.linkActive};
      }
    `;
  }}
`;

export {LinkButton};
