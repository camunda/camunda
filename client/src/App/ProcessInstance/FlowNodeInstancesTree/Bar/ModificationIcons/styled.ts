/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';
import {ReactComponent as Plus} from 'modules/components/Icon/plus.svg';
import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

const Container = styled.div`
  display: flex;
  align-items: center;
  margin: 0 12px;
`;

const IconStyles = css`
  margin: 0 3px;
  ${({theme}) => {
    return css`
      color: ${theme.colors.flowNodeInstancesTree.bar.placeholder
        .modificationIcon.color};
    `;
  }}
`;

const PlusIcon = styled(Plus)`
  ${IconStyles}
`;

const StopIcon = styled(Stop)`
  ${IconStyles}
`;

const WarningIcon = styled(Warning)`
  width: 15px;
  margin: 0 3px;

  ${({theme}) => {
    return css`
      color: ${theme.colors.filtersAndWarnings};
    `;
  }}
`;

export {Container, PlusIcon, StopIcon, WarningIcon};
