/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Link} from 'react-router-dom';

type PanelListItemProps = {
  $boxSize?: 'small';
};

const PanelListItem = styled(Link)<PanelListItemProps>`
  ${({theme, $boxSize}) => {
    const colors = theme.colors.panelListItem;
    const shadows = theme.shadows.panelListItem;
    const isSmall = $boxSize === 'small';

    return css`
      height: ${isSmall ? 39 : 45}px;
      display: block;
      padding: ${isSmall ? '5px 8px 6px' : '8px 8px 7px'};
      margin-left: 24px;
      border: 1px solid transparent;
      border-radius: 3px;

      &:hover {
        box-shadow: ${shadows.hover};
        border-color: ${colors.hover.borderColor};
      }

      &:active {
        box-shadow: ${shadows.active};
        border-color: ${colors.active.borderColor};
      }
    `;
  }}
`;

export {PanelListItem};
