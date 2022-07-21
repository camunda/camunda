/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {INCIDENTS_BAR_HEIGHT} from 'modules/constants';
import {styles} from '@carbon/elements';

const BannerButton = styled.button`
  ${({theme}) => {
    return css`
      position: relative;
      z-index: 4;

      height: ${INCIDENTS_BAR_HEIGHT}px;
      ${styles.bodyShort01};
      font-weight: 500;

      background-color: ${theme.colors.incidentsAndErrors};
      color: ${theme.colors.white};

      cursor: pointer;
    `;
  }}
`;

const ViewHide = styled.span`
  margin-left: 20px;
  text-decoration: underline;
`;

export {BannerButton, ViewHide};
