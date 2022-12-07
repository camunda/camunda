/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {styles} from '@carbon/elements';
import styled, {css} from 'styled-components';
import {Anchor as BaseAnchor} from 'modules/components/Anchor/styled';

const Grid = styled.div`
  display: grid;
  justify-content: center;
  align-content: center;
  grid-template-columns: 80px 334px;
  column-gap: 24px;
  height: 100%;
`;

const Title = styled.h3`
  ${styles.productiveHeading02};
  ${({theme}) => {
    return css`
      color: ${theme.colors.emptyState.color};
      margin: 0;
      padding: 0;
    `;
  }}
`;

const Description = styled.p`
  ${({theme}) => {
    return css`
      color: ${theme.colors.emptyState.color};
    `;
  }}
  margin-bottom: 24px;
`;

const LinkContainer = styled.div`
  margin-top: 16px;
`;

const Anchor = styled(BaseAnchor)`
  font-size: 14px;
`;

export {Grid, Title, Description, LinkContainer, Anchor};
