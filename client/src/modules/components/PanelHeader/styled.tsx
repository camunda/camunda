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
    const colors = theme.colors.decisionsList.header;

    return css`
      display: flex;
      min-height: 37px;
      height: 37px;
      max-height: 37px;
      align-items: center;
      background-color: ${colors.backgroundColor};
      padding: 8px 0 8px 19px;
      border-bottom: solid 1px ${theme.colors.borderColor};
    `;
  }}
`;

const InstancesCount = styled.span`
  ${({theme}) => {
    return css`
      border-left: 1px solid ${theme.colors.borderColor};
      padding-left: 30px;
      ${styles.bodyShort01};
      margin-left: 34px;
    `;
  }}
`;

const Title = styled.h2`
  ${({theme}) => {
    return css`
      ${styles.productiveHeading02};
      color: ${theme.colors.text01};
      margin: 0;
    `;
  }}
`;

export {Container, Title, InstancesCount};
