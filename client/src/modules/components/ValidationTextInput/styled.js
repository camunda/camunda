/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

const InputContainer = styled.div`
  position: relative;
`;

const WarningIcon = styled(Warning)`
  ${({theme}) => {
    return css`
      position: absolute;
      width: 16px;
      fill: ${theme.colors.incidentsAndErrors};
      top: 3px;
      right: -16px;
    `;
  }}
`;

export {InputContainer, WarningIcon};
