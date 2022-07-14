/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {Panel} from 'modules/components/Panel';
import {StatusMessage} from 'modules/components/StatusMessage';
import {styles} from '@carbon/elements';

const VariablesPanel = styled(Panel)`
  ${({theme}) => {
    return css`
      ${styles.bodyShort01};
      border-left: none;
      color: ${theme.colors.text01};

      ${StatusMessage} {
        height: 58%;
      }
    `;
  }}
`;
export {VariablesPanel};
