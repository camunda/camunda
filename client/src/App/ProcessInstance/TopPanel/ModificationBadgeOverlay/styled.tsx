/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as DefaultMinus} from 'modules/components/Icon/minus.svg';

const Modifications = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.diagram.modificationsBadgeOverlay;

    return css`
      ${styles.label01};
      font-weight: bold;
      padding: 4px 6px 4px 5px;
      min-width: 44px;
      white-space: nowrap;
      display: flex;
      justify-content: center;
      align-items: center;
      border-radius: 12px;
      transform: translateX(-50%);
      background-color: ${colors.backgroundColor};
      color: ${theme.colors.white};
    `;
  }}
`;

const PlusIcon = styled(DefaultPlus)`
  width: 14px;
  height: 14px;
`;

const MinusIcon = styled(DefaultMinus)`
  width: 14px;
  height: 14px;
`;

export {Modifications, PlusIcon, MinusIcon};
