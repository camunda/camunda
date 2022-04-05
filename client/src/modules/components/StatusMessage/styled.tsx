/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent as Check} from 'modules/components/Icon/check.svg';
import {ReactComponent as Warning} from 'modules/components/Icon/warning.svg';

const CheckIcon = styled(Check)``;
const WarningIcon = styled(Warning)``;

type Props = {
  $variant: 'default' | 'success' | 'error';
};

const Container = styled.div<Props>`
  ${({theme, $variant = 'default'}) => {
    const colors = theme.colors.dashboard.message;

    return css`
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      color: ${colors[$variant].color};

      ${CheckIcon}, ${WarningIcon} {
        margin-right: 15px;
      }
    `;
  }}
`;

export {Container, CheckIcon, WarningIcon};
