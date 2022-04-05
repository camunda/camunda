/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import defaultPill from 'modules/components/Pill';

const Container = styled.div`
  ${({theme}) => {
    return css`
      display: flex;
      align-items: center;
      position: relative;
      margin-left: 12px;
      padding-left: 19px;

      &:before {
        content: '';
        position: absolute;
        top: -5px;
        left: 0;
        height: 32px;
        width: 1px;
        background: ${theme.colors.borderColor};
      }
    `;
  }}
`;

const Pill = styled(defaultPill)`
  height: 22px;
  padding: 2px 11px 2px 10px;
  > svg {
    margin-right: 5px;
  }
`;

export {Container, Pill};
