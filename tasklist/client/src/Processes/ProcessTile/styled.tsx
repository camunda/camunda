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
  height: 100%;
  padding: var(--cds-spacing-05);
  background: var(--cds-layer);
`;

const Content = styled.div`
  height: 100%;
`;

const TitleWrapper = styled.div`
  height: fit-content;
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
    ${theme.productiveHeading02};
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

const ButtonRow = styled.div`
  display: flex;
  flex-direction: row-reverse;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--cds-spacing-05);
  align-content: end;

  & .startButton {
    margin-right: auto;
  }
`;

export {Container, Content, TitleWrapper, Title, Subtitle, ButtonRow};
