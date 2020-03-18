/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

import Spinner from 'modules/components/Spinner';

import {Colors, themed, themeStyle} from 'modules/theme';

export const OperationSpinner = themed(styled(Spinner)`
  margin: 0 5px;
 border: 3px solid ${({selected}) =>
   themeStyle({
     dark: '#ffffff',
     light: selected ? Colors.selections : Colors.badge02
   })};
    border-right-color: transparent;
  }
`);
