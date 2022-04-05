/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {createGlobalStyle} from 'styled-components';
import Modal from 'modules/components/Modal';

const JSONEditorStyles = createGlobalStyle`
  div.jsoneditor {
    border: none;
  }
`;

const Body = styled(Modal.Body)`
  padding: 0;
`;

export {JSONEditorStyles, Body};
