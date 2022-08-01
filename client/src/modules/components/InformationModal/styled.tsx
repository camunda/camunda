/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import Modal from 'modules/components/Modal';

const Body = styled(Modal.Body)`
  padding-top: 0;
`;

const Footer = styled(Modal.Footer)`
  > :not(:last-child) {
    margin-right: 15px;
  }
`;

export {Body, Footer};
