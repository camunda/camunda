/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {IconButton} from 'modules/components/IconButton';

const Button = styled(IconButton)`
  ${({theme}) => {
    const colors = theme.colors.variables.editButton;

    return css`
      margin-left: 8px;
      z-index: 0;

      svg {
        margin-top: 4px;
      }

      &:disabled,
      &:disabled :hover {
        svg {
          color: ${colors.disabled.color};
          opacity: 0.5;
        }

        &:before {
          background-color: transparent;
        }
      }
    `;
  }}
`;

export {Button};
