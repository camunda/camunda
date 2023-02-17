/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CmButton} from '@camunda-cloud/common-ui-react';
import {bodyShort01, productiveHeading02} from '@carbon/elements';
import styled, {css} from 'styled-components';

const Th = styled.th`
  ${({theme}) => {
    const {color, backgroundColor} =
      theme.colors.resourceDeletionModal.detailsTable;
    return css`
      ${productiveHeading02};
      padding: 20px 20px 4px 20px;
      text-align: left;
      color: ${color};
      background-color: ${backgroundColor};
    `;
  }}
`;

const Td = styled.td`
  ${({theme}) => {
    const {border} = theme.colors.resourceDeletionModal.detailsTable;
    return css`
      ${bodyShort01};
      padding: 11px 20px;
      border-bottom: 1px solid ${border};
    `;
  }}
`;

const DeleteButton = styled(CmButton)`
  margin-left: 15px;
  width: 117px;
`;

const Description = styled.p`
  margin: 10px 0 24px 0;
`;

export {Th, Td, DeleteButton, Description};
