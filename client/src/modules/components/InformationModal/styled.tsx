/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import Modal from 'modules/components/Modal';

const Body = styled(Modal.Body)`
  ${({theme}) => {
    return css`
      padding-top: 0;
      color: ${theme.colors.text01};
      overflow: auto;
    `;
  }}
`;

export {Body};
