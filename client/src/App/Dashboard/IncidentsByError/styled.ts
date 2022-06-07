/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import {Bar} from 'modules/components/InstancesBar';

const Li = styled.li`
  margin: 0 10px 10px 0;
`;

const InstancesBarStyles: ThemedInterpolationFunction = ({theme}) => {
  return css`
    ${Bar} {
      border-radius: 2px;
      opacity: 1;
      background: ${theme.colors.ui05};
    }
  `;
};

export {Li, InstancesBarStyles};
