/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {createGlobalStyle} from 'styled-components';

const GlobalStyle = createGlobalStyle`
  html,
    body,
    #root {
      -moz-osx-font-smoothing: antialiased;
      -webkit-font-smoothing: antialiased;
      width: 100%;
      height: 100%;
      overflow: hidden;
      margin: 0;
      padding: 0;
    }

    svg {
      fill: currentColor;
    }
`;

export {GlobalStyle};
