/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {createGlobalStyle} from 'styled-components';

const JSONEditorStyles = createGlobalStyle`
  div.jsoneditor {
    border: none;

    & .ace_error {
      background-image: none;
    }
  }
`;

const Container = styled.div`
  width: 100%;
  height: 100%;
`;

export {Container, JSONEditorStyles};
