/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const EmptyMessage = styled.div`
  ${({theme}) => {
    const colors = theme.colors.emptyMessage;

    return css`
      padding-top: 40px;
      ${styles.bodyShort02};
      text-align: center;

      color: ${colors.color};

      span {
        display: block;
      }
    `;
  }}
`;

export {EmptyMessage};
