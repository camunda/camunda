/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors} from 'modules/theme';

export const InputContainer = styled.div`
  position: relative;
`;

export const WarningIcon = styled.div`
  position: absolute;
  color: ${Colors.incidentsAndErrors};

  // change when icon is there
  font-weight: bold;
  right: 3px;
  top: 7px;
`;
