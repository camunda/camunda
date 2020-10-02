/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Spinner from 'modules/components/Spinner';
import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const OperationSpinner = themed(styled(Spinner)`
  margin: 0 5px;
  width: 14px;
  height: 14px;

  border: 2px solid
    ${({selected}) =>
      themeStyle({
        dark: '#ffffff',
        light: selected ? Colors.selections : Colors.uiLight06,
      })};
  border-right-color: transparent;
`);

export {OperationSpinner};
