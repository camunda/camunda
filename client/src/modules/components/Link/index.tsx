/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Link as BaseLink} from 'react-router-dom';
import styled, {css} from 'styled-components';

const Link = styled(BaseLink)`
  ${({theme}) => {
    return css`
      text-decoration: underline;

      &:link {
        color: ${theme.colors.linkDefault};
      }

      &:hover {
        color: ${theme.colors.linkHover};
      }

      &:active {
        color: ${theme.colors.linkActive};
      }

      &:visited {
        color: ${theme.colors.linkVisited};
      }
    `;
  }}
`;

export {Link};
