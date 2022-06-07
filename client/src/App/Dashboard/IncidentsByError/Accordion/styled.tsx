/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import BaseInstancesBar, {Wrapper} from 'modules/components/InstancesBar';
import {InstancesBarStyles} from '../styled';

const InstancesBar = styled(BaseInstancesBar)`
  ${({theme}) => {
    return css`
      ${InstancesBarStyles};

      ${Wrapper} {
        color: ${theme.colors.incidentsAndErrors};
      }
    `;
  }}
`;

export {InstancesBar};
