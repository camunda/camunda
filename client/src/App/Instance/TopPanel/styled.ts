/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {StatusMessage as BaseStatusMessage} from 'modules/components/StatusMessage';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.topPanel;

    return css`
      background-color: ${colors.backgroundColor};
      height: 100%;
      display: flex;
      flex-direction: column;
    `;
  }}
`;

const DiagramPanel = styled.div`
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  position: relative;
`;

const StatusMessage = styled(BaseStatusMessage)`
  height: 100%;
`;

export {Container, DiagramPanel, StatusMessage};
