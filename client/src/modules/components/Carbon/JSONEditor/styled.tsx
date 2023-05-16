/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createGlobalStyle} from 'styled-components';

const EditorStyles = createGlobalStyle`
  .monaco-editor textarea:focus {
    box-shadow: none !important;
  }
`;

export {EditorStyles};
