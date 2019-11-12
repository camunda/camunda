/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {themed, themeStyle} from 'modules/theme';
import EmptyPanelComponent from 'modules/components/EmptyPanel';

import Panel from 'modules/components/Panel';

export const Variables = themed(styled(Panel)`
  flex: 1;
  font-size: 14px;

  border-left: none;
  color: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.8)',
    light: 'rgba(98, 98, 110, 0.8)'
  })};
`);

export const EmptyPanel = styled(EmptyPanelComponent)`
  position: absolute;
  top: 20px;
  z-index: 1;
`;
