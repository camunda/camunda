/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Copyright = themed(styled.div`
  color: ${themeStyle({dark: '#fff', light: Colors.uiLight06})};
  opacity: ${themeStyle({dark: 0.7, light: 0.9})};
  font-size: 12px;
`);
