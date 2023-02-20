/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  CmButton,
  CmCheckbox as BaseCmCheckbox,
} from '@camunda-cloud/common-ui-react';
import {WarningMessage} from 'modules/components/Messages/WarningMessage';
import Modal from 'modules/components/Modal';

const WarningContainer = styled(WarningMessage)`
  display: flex;
  margin: 24px 0;
`;

const DeleteButton = styled(CmButton)`
  margin-left: 15px;
  width: 117px;
`;

const Description = styled.p`
  margin: 10px 0 24px 0;
`;

const ModalBody = styled(Modal.Body)`
  ${({theme}) => {
    return css`
      padding-bottom: 24px;
      color: ${theme.colors.text01};
    `;
  }}
`;

const CmCheckbox = styled(BaseCmCheckbox)`
  margin-top: 24px;
`;

export {WarningContainer, DeleteButton, Description, ModalBody, CmCheckbox};
