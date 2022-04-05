/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import BaseInstancesBar, {Wrapper, Bar} from 'modules/components/InstancesBar';

const Li = styled.li`
  margin: 0 10px 10px 0;
`;

const VersionUl = styled.ul`
  margin-top: 8px;
  margin-bottom: 16px;
`;

const VersionLi = styled.li`
  margin: 6px 0 0;
  padding: 0;
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

const LiInstancesBar = styled(BaseInstancesBar)`
  ${({theme}) => {
    return css`
      ${InstancesBarStyles};

      ${Wrapper} {
        color: ${theme.colors.incidentsAndErrors};
      }
    `;
  }}
`;

const VersionLiInstancesBar = styled(BaseInstancesBar)`
  ${InstancesBarStyles}
`;

export {Li, VersionUl, VersionLi, LiInstancesBar, VersionLiInstancesBar};
