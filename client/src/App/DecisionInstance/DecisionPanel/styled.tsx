/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const Container = styled.div`
  ${({theme}) => {
    return css`
      background: ${theme.colors.decisionViewer.background};
      overflow: auto;
      height: 100%;
    `;
  }}
`;
const IncidentBanner = styled.div`
  ${({theme}) => {
    return css`
      background-color: ${theme.colors.incidentsAndErrors};
      color: ${theme.colors.white};
      ${styles.bodyShort01};
      font-weight: 500;
      height: 42px;
      display: flex;
      align-items: center;
      justify-content: center;
    `;
  }}
`;

export {IncidentBanner, Container};
