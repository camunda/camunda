/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import styled, {css} from 'styled-components';

const Container = styled.div`
  width: 100%;
  padding: var(--cds-spacing-05);
  background: var(--cds-layer);
`;

const textOverflowEllipsis = css`
  max-width: 100%;
  text-overflow: ellipsis;
  white-space: nowrap;
  overflow: hidden;
`;

const Title = styled.h4`
  ${({theme}) => css`
    color: var(--cds-text-primary);
    ${theme.productiveHeading03};
    ${textOverflowEllipsis};
  `}
`;

const Subtitle = styled.span`
  ${({theme}) => css`
    min-height: ${rem(16)};
    margin-bottom: var(--cds-spacing-06);
    color: var(--cds-text-secondary);
    ${theme.label01};
    ${textOverflowEllipsis};
  `}
`;

export {Container, Title, Subtitle};
