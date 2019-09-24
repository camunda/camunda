/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors} from 'modules/theme';

import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

export const InputContainer = styled.div`
  position: relative;
`;

export const WarningIcon = styled(Warning)`
  position: absolute;
  width: 16px;
  fill: ${Colors.incidentsAndErrors};
  top: 3px;
  right: -16px;
`;
