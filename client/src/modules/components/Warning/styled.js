/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

const Container = styled.span`
  padding: 3px 5px;
`;

const WarningIcon = styled(Warning)`
  ${({theme}) => {
    return css`
      width: 16px;
      fill: ${theme.colors.incidentsAndErrors};
    `;
  }}
`;

export {Container, WarningIcon};
